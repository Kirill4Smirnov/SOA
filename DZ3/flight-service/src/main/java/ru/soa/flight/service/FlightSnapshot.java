package ru.soa.flight.service;

import ru.soa.flight.domain.Flight;
import ru.soa.flight.domain.FlightStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FlightSnapshot(
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
    public static FlightSnapshot from(Flight flight) {
        return new FlightSnapshot(
                flight.getId(), flight.getFlightNumber(), flight.getAirline().getCode(),
                flight.getAirline().getName(), flight.getOrigin().getCode(), flight.getDestination().getCode(),
                flight.getDepartureTime(), flight.getArrivalTime(), flight.getTotalSeats(),
                flight.getAvailableSeats(), flight.getPrice(), flight.getStatus());
    }
}

