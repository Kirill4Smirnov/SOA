package ru.soa.booking.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.soa.flight.contract.v1.Flight;
import ru.soa.flight.contract.v1.FlightServiceGrpc;
import ru.soa.flight.contract.v1.GetFlightRequest;
import ru.soa.flight.contract.v1.ReleaseReservationRequest;
import ru.soa.flight.contract.v1.ReserveSeatsRequest;
import ru.soa.flight.contract.v1.SearchFlightsRequest;
import ru.soa.flight.contract.v1.SearchFlightsResponse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class FlightGateway {
    private static final Logger log = LoggerFactory.getLogger(FlightGateway.class);

    private final FlightServiceGrpc.FlightServiceBlockingStub stub;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration deadline;

    public FlightGateway(ManagedChannel channel,
                         @Value("${flight.grpc.retry.max-attempts}") int maxAttempts,
                         @Value("${flight.grpc.retry.initial-backoff}") Duration initialBackoff,
                         @Value("${flight.grpc.deadline}") Duration deadline) {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("gRPC retry max-attempts must be between 1 and 3");
        }
        this.stub = FlightServiceGrpc.newBlockingStub(channel);
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.deadline = deadline;
    }

    public SearchFlightsResponse search(String origin, String destination, LocalDate date) {
        var request = SearchFlightsRequest.newBuilder().setOrigin(origin).setDestination(destination);
        if (date != null) {
            var instant = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            request.setDepartureDate(Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()));
        }
        return call(() -> withDeadline().searchFlights(request.build()));
    }

    public Flight get(UUID id) {
        return call(() -> withDeadline().getFlight(
                GetFlightRequest.newBuilder().setFlightId(id.toString()).build()));
    }

    public void reserve(UUID flightId, int seatCount, UUID bookingId) {
        var request = ReserveSeatsRequest.newBuilder()
                .setFlightId(flightId.toString())
                .setSeatCount(seatCount)
                .setBookingId(bookingId.toString())
                .build();
        call(() -> withDeadline().reserveSeats(request));
    }

    public void release(UUID bookingId) {
        var request = ReleaseReservationRequest.newBuilder().setBookingId(bookingId.toString()).build();
        call(() -> withDeadline().releaseReservation(request));
    }

    private FlightServiceGrpc.FlightServiceBlockingStub withDeadline() {
        return stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    private <T> T call(Supplier<T> invocation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return invocation.get();
            } catch (StatusRuntimeException exception) {
                if (!retryable(exception) || attempt >= maxAttempts || circuitOpen(exception)) throw exception;
                long delay = initialBackoff.toMillis() * (1L << (attempt - 1));
                log.warn("gRPC call failed code={} attempt={}/{}, retry_in_ms={}",
                        exception.getStatus().getCode(), attempt, maxAttempts, delay);
                sleep(delay);
            }
        }
    }

    private boolean retryable(StatusRuntimeException exception) {
        return exception.getStatus().getCode() == Status.Code.UNAVAILABLE
                || exception.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED;
    }

    private boolean circuitOpen(StatusRuntimeException exception) {
        String description = exception.getStatus().getDescription();
        return description != null && description.contains("circuit breaker is OPEN");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.withDescription("Retry interrupted").withCause(exception).asRuntimeException();
        }
    }
}
