package ru.soa.booking.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.Timestamp;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.soa.flight.contract.v1.Flight;
import ru.soa.flight.contract.v1.FlightStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record FlightResponse(
        UUID id,
        @JsonProperty("flight_number") @Schema(name = "flight_number")
        String flightNumber,
        @JsonProperty("airline_code") @Schema(name = "airline_code")
        String airlineCode,
        @JsonProperty("airline_name") @Schema(name = "airline_name")
        String airlineName,
        String origin,
        String destination,
        @JsonProperty("departure_time") @Schema(name = "departure_time")
        OffsetDateTime departureTime,
        @JsonProperty("arrival_time") @Schema(name = "arrival_time")
        OffsetDateTime arrivalTime,
        @JsonProperty("total_seats") @Schema(name = "total_seats")
        int totalSeats,
        @JsonProperty("available_seats") @Schema(name = "available_seats")
        int availableSeats,
        BigDecimal price,
        FlightStatus status
) {
    public static FlightResponse from(Flight flight) {
        return new FlightResponse(
                UUID.fromString(flight.getId()), flight.getFlightNumber(), flight.getAirlineCode(),
                flight.getAirlineName(), flight.getOrigin(), flight.getDestination(),
                time(flight.getDepartureTime()), time(flight.getArrivalTime()), flight.getTotalSeats(),
                flight.getAvailableSeats(), new BigDecimal(flight.getPrice()), flight.getStatus());
    }

    private static OffsetDateTime time(Timestamp value) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(value.getSeconds(), value.getNanos()), ZoneOffset.UTC);
    }
}

