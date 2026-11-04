package ru.soa.booking.web.dto;

import com.google.protobuf.Timestamp;
import ru.soa.flight.contract.v1.Flight;
import ru.soa.flight.contract.v1.FlightStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record FlightResponse(
        UUID id,
        String flightNumber,
        String airlineCode,
        String airlineName,
        String origin,
        String destination,
        OffsetDateTime departureTime,
        OffsetDateTime arrivalTime,
        int totalSeats,
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

