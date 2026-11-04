package ru.soa.flight.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.soa.flight.domain.Flight;
import ru.soa.flight.domain.FlightStatus;
import ru.soa.flight.domain.ReservationStatus;
import ru.soa.flight.domain.SeatReservation;
import ru.soa.flight.repository.FlightRepository;
import ru.soa.flight.repository.SeatReservationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class FlightManagementService {
    private final FlightRepository flights;
    private final SeatReservationRepository reservations;
    private final FlightCache cache;

    public FlightManagementService(FlightRepository flights, SeatReservationRepository reservations,
                                   FlightCache cache) {
        this.flights = flights;
        this.reservations = reservations;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public FlightSnapshot get(UUID id) {
        return cache.getFlight(id).orElseGet(() -> {
            FlightSnapshot snapshot = flights.findById(id).map(FlightSnapshot::from)
                    .orElseThrow(() -> status(Status.NOT_FOUND, "Flight not found"));
            cache.putFlight(snapshot);
            return snapshot;
        });
    }

    @Transactional(readOnly = true)
    public List<FlightSnapshot> search(String origin, String destination, LocalDate date) {
        String key = cache.searchKey(origin, destination, date == null ? "all" : date.toString());
        return cache.getSearch(key).orElseGet(() -> {
            List<Flight> found;
            if (date == null) {
                found = flights.search(origin, destination);
            } else {
                OffsetDateTime from = date.atStartOfDay().atOffset(ZoneOffset.UTC);
                found = flights.searchByDate(origin, destination, from, from.plusDays(1));
            }
            List<FlightSnapshot> result = found.stream().map(FlightSnapshot::from).toList();
            cache.putSearch(key, result);
            return result;
        });
    }

    @Transactional
    public ReservationSnapshot reserve(UUID flightId, int seatCount, UUID bookingId) {
        Flight flight = flights.findByIdForUpdate(flightId)
                .orElseThrow(() -> status(Status.NOT_FOUND, "Flight not found"));

        SeatReservation existing = reservations.findByBookingId(bookingId).orElse(null);
        if (existing != null) {
            if (!existing.getFlight().getId().equals(flightId) || existing.getSeatCount() != seatCount) {
                throw status(Status.ALREADY_EXISTS, "booking_id already used with other reservation parameters");
            }
            return ReservationSnapshot.from(existing);
        }
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            throw status(Status.FAILED_PRECONDITION, "Flight is not available for booking");
        }
        if (flight.getAvailableSeats() < seatCount) {
            throw status(Status.RESOURCE_EXHAUSTED, "Not enough available seats");
        }

        flight.reserve(seatCount);
        SeatReservation reservation = reservations.save(
                new SeatReservation(UUID.randomUUID(), flight, bookingId, seatCount));
        invalidateAfterCommit(flight);
        return ReservationSnapshot.from(reservation);
    }

    @Transactional
    public ReservationSnapshot release(UUID bookingId) {
        SeatReservation reservation = reservations.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> status(Status.NOT_FOUND, "Active reservation not found"));
        if (reservation.getStatus() == ReservationStatus.ACTIVE) {
            reservation.getFlight().release(reservation.getSeatCount());
            reservation.release();
            invalidateAfterCommit(reservation.getFlight());
        }
        return ReservationSnapshot.from(reservation);
    }

    @Transactional
    public FlightSnapshot update(UUID flightId, BigDecimal price, FlightStatus status) {
        Flight flight = flights.findByIdForUpdate(flightId)
                .orElseThrow(() -> status(Status.NOT_FOUND, "Flight not found"));
        flight.update(price, status);
        invalidateAfterCommit(flight);
        return FlightSnapshot.from(flight);
    }

    private void invalidateAfterCommit(Flight flight) {
        UUID flightId = flight.getId();
        String origin = flight.getOrigin().getCode();
        String destination = flight.getDestination().getCode();
        LocalDate departureDate = flight.getDepartureTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        Runnable invalidation = () -> cache.invalidate(flightId, origin, destination, departureDate);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
        } else {
            invalidation.run();
        }
    }

    private StatusRuntimeException status(Status status, String description) {
        return status.withDescription(description).asRuntimeException();
    }
}
