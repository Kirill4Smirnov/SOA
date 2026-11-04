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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seat_reservations")
public class SeatReservation {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    protected SeatReservation() {}

    public SeatReservation(UUID id, Flight flight, UUID bookingId, int seatCount) {
        this.id = id;
        this.flight = flight;
        this.bookingId = bookingId;
        this.seatCount = seatCount;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Flight getFlight() { return flight; }
    public UUID getBookingId() { return bookingId; }
    public int getSeatCount() { return seatCount; }
    public ReservationStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }

    public void release() {
        status = ReservationStatus.RELEASED;
        releasedAt = OffsetDateTime.now();
    }
}

