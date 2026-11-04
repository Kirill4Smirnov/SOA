package ru.soa.booking.web.dto;

import ru.soa.booking.domain.Booking;
import ru.soa.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID userId,
        UUID flightId,
        String passengerName,
        String passengerEmail,
        int seatCount,
        BigDecimal totalPrice,
        BookingStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime cancelledAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getUserId(), booking.getFlightId(), booking.getPassengerName(),
                booking.getPassengerEmail(), booking.getSeatCount(), booking.getTotalPrice(),
                booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt());
    }
}

