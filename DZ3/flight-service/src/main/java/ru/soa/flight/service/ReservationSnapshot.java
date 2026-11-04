package ru.soa.flight.service;

import ru.soa.flight.domain.ReservationStatus;
import ru.soa.flight.domain.SeatReservation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationSnapshot(
        UUID id,
        UUID flightId,
        UUID bookingId,
        int seatCount,
        ReservationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime releasedAt
) {
    public static ReservationSnapshot from(SeatReservation reservation) {
        return new ReservationSnapshot(
                reservation.getId(), reservation.getFlight().getId(), reservation.getBookingId(),
                reservation.getSeatCount(), reservation.getStatus(), reservation.getCreatedAt(),
                reservation.getReleasedAt());
    }
}

