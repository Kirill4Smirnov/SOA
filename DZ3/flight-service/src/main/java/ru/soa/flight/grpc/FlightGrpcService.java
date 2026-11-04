package ru.soa.flight.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.soa.flight.contract.v1.FlightServiceGrpc;
import ru.soa.flight.contract.v1.GetFlightRequest;
import ru.soa.flight.contract.v1.ReleaseReservationRequest;
import ru.soa.flight.contract.v1.ReserveSeatsRequest;
import ru.soa.flight.contract.v1.SearchFlightsRequest;
import ru.soa.flight.contract.v1.SearchFlightsResponse;
import ru.soa.flight.contract.v1.UpdateFlightRequest;
import ru.soa.flight.service.FlightManagementService;
import ru.soa.flight.service.FlightSnapshot;
import ru.soa.flight.service.ReservationSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@GrpcService
public class FlightGrpcService extends FlightServiceGrpc.FlightServiceImplBase {
    private final FlightManagementService service;

    public FlightGrpcService(FlightManagementService service) {
        this.service = service;
    }

    @Override
    public void searchFlights(SearchFlightsRequest request, StreamObserver<SearchFlightsResponse> observer) {
        execute(observer, () -> {
            String origin = iata(request.getOrigin(), "origin");
            String destination = iata(request.getDestination(), "destination");
            if (origin.equals(destination)) invalid("origin and destination must differ");
            LocalDate date = request.hasDepartureDate()
                    ? instant(request.getDepartureDate()).atZone(ZoneOffset.UTC).toLocalDate() : null;
            var response = SearchFlightsResponse.newBuilder();
            service.search(origin, destination, date).stream().map(this::flight).forEach(response::addFlights);
            return response.build();
        });
    }

    @Override
    public void getFlight(GetFlightRequest request, StreamObserver<ru.soa.flight.contract.v1.Flight> observer) {
        execute(observer, () -> flight(service.get(uuid(request.getFlightId(), "flight_id"))));
    }

    @Override
    public void reserveSeats(ReserveSeatsRequest request,
                             StreamObserver<ru.soa.flight.contract.v1.SeatReservation> observer) {
        execute(observer, () -> {
            if (request.getSeatCount() <= 0) invalid("seat_count must be positive");
            return reservation(service.reserve(
                    uuid(request.getFlightId(), "flight_id"), request.getSeatCount(),
                    uuid(request.getBookingId(), "booking_id")));
        });
    }

    @Override
    public void releaseReservation(ReleaseReservationRequest request,
                                   StreamObserver<ru.soa.flight.contract.v1.SeatReservation> observer) {
        execute(observer, () -> reservation(service.release(uuid(request.getBookingId(), "booking_id"))));
    }

    @Override
    public void updateFlight(UpdateFlightRequest request,
                             StreamObserver<ru.soa.flight.contract.v1.Flight> observer) {
        execute(observer, () -> {
            BigDecimal price;
            try {
                price = new BigDecimal(request.getPrice());
            } catch (NumberFormatException exception) {
                return invalid("price must be a decimal number");
            }
            if (price.signum() <= 0) invalid("price must be positive");
            ru.soa.flight.domain.FlightStatus status;
            try {
                status = ru.soa.flight.domain.FlightStatus.valueOf(request.getStatus().name());
            } catch (IllegalArgumentException exception) {
                return invalid("status must be specified");
            }
            return flight(service.update(uuid(request.getFlightId(), "flight_id"), price, status));
        });
    }

    private ru.soa.flight.contract.v1.Flight flight(FlightSnapshot value) {
        return ru.soa.flight.contract.v1.Flight.newBuilder()
                .setId(value.id().toString())
                .setFlightNumber(value.flightNumber())
                .setAirlineCode(value.airlineCode())
                .setAirlineName(value.airlineName())
                .setOrigin(value.origin())
                .setDestination(value.destination())
                .setDepartureTime(timestamp(value.departureTime().toInstant()))
                .setArrivalTime(timestamp(value.arrivalTime().toInstant()))
                .setTotalSeats(value.totalSeats())
                .setAvailableSeats(value.availableSeats())
                .setPrice(value.price().toPlainString())
                .setStatus(ru.soa.flight.contract.v1.FlightStatus.valueOf(value.status().name()))
                .build();
    }

    private ru.soa.flight.contract.v1.SeatReservation reservation(ReservationSnapshot value) {
        var builder = ru.soa.flight.contract.v1.SeatReservation.newBuilder()
                .setId(value.id().toString())
                .setFlightId(value.flightId().toString())
                .setBookingId(value.bookingId().toString())
                .setSeatCount(value.seatCount())
                .setStatus(ru.soa.flight.contract.v1.ReservationStatus.valueOf(value.status().name()))
                .setCreatedAt(timestamp(value.createdAt().toInstant()));
        if (value.releasedAt() != null) builder.setReleasedAt(timestamp(value.releasedAt().toInstant()));
        return builder.build();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private Instant instant(Timestamp value) {
        try {
            return Instant.ofEpochSecond(value.getSeconds(), value.getNanos());
        } catch (RuntimeException exception) {
            throw Status.INVALID_ARGUMENT.withDescription("invalid timestamp").asRuntimeException();
        }
    }

    private UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw Status.INVALID_ARGUMENT.withDescription(field + " must be UUID").asRuntimeException();
        }
    }

    private String iata(String value, String field) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) invalid(field + " must be a three-letter IATA code");
        return normalized;
    }

    private <T> T invalid(String message) {
        throw Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
    }

    private <T> void execute(StreamObserver<T> observer, Supplier<T> operation) {
        try {
            observer.onNext(operation.get());
            observer.onCompleted();
        } catch (StatusRuntimeException exception) {
            observer.onError(exception);
        } catch (RuntimeException exception) {
            observer.onError(Status.INTERNAL.withDescription("Internal Flight Service error")
                    .withCause(exception).asRuntimeException());
        }
    }
}
