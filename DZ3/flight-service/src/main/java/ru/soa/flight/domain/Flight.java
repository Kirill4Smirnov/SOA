package ru.soa.flight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flights")
public class Flight {
    @Id
    private UUID id;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "airline_code", nullable = false)
    private Airline airline;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "origin_code", nullable = false)
    private Airport origin;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_code", nullable = false)
    private Airport destination;

    @Column(name = "departure_time", nullable = false)
    private OffsetDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private OffsetDateTime arrivalTime;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlightStatus status;

    @Version
    private long version;

    protected Flight() {}

    public UUID getId() { return id; }
    public String getFlightNumber() { return flightNumber; }
    public Airline getAirline() { return airline; }
    public Airport getOrigin() { return origin; }
    public Airport getDestination() { return destination; }
    public OffsetDateTime getDepartureTime() { return departureTime; }
    public OffsetDateTime getArrivalTime() { return arrivalTime; }
    public int getTotalSeats() { return totalSeats; }
    public int getAvailableSeats() { return availableSeats; }
    public BigDecimal getPrice() { return price; }
    public FlightStatus getStatus() { return status; }

    public void reserve(int count) { availableSeats -= count; }
    public void release(int count) { availableSeats += count; }
    public void update(BigDecimal newPrice, FlightStatus newStatus) {
        price = newPrice;
        status = newStatus;
    }
}

