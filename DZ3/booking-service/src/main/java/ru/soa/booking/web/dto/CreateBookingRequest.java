package ru.soa.booking.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID userId,
        @NotNull UUID flightId,
        @NotBlank @Size(max = 200) String passengerName,
        @NotBlank @Email @Size(max = 320) String passengerEmail,
        @Min(1) @Max(20) int seatCount
) {}

