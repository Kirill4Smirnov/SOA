package ru.soa.booking.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBookingRequest(
        @JsonProperty("user_id") @Schema(name = "user_id", example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        @NotNull UUID userId,

        @JsonProperty("flight_id") @Schema(name = "flight_id", example = "11111111-1111-1111-1111-111111111111")
        @NotNull UUID flightId,

        @JsonProperty("passenger_name") @Schema(name = "passenger_name", example = "Ivan Petrov")
        @NotBlank @Size(max = 200) String passengerName,

        @JsonProperty("passenger_email") @Schema(name = "passenger_email", example = "ivan@example.com")
        @NotBlank @Email @Size(max = 320) String passengerEmail,

        @JsonProperty("seat_count") @Schema(name = "seat_count", example = "2")
        @Min(1) @Max(20) int seatCount
) {}

