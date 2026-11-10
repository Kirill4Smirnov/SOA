package ru.soa.booking.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.soa.booking.domain.Booking;
import ru.soa.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        @JsonProperty("user_id") @Schema(name = "user_id")
        UUID userId,
        @JsonProperty("flight_id") @Schema(name = "flight_id")
        UUID flightId,
        @JsonProperty("passenger_name") @Schema(name = "passenger_name")
        String passengerName,
        @JsonProperty("passenger_email") @Schema(name = "passenger_email")
        String passengerEmail,
        @JsonProperty("seat_count") @Schema(name = "seat_count")
        int seatCount,
        @JsonProperty("total_price") @Schema(name = "total_price")
        BigDecimal totalPrice,
        BookingStatus status,
        @JsonProperty("created_at") @Schema(name = "created_at")
        OffsetDateTime createdAt,
        @JsonProperty("cancelled_at") @Schema(name = "cancelled_at")
        OffsetDateTime cancelledAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getUserId(), booking.getFlightId(), booking.getPassengerName(),
                booking.getPassengerEmail(), booking.getSeatCount(), booking.getTotalPrice(),
                booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt());
    }
}

