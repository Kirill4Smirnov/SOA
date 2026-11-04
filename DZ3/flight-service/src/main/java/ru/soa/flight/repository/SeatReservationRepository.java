package ru.soa.flight.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.soa.flight.domain.SeatReservation;

import java.util.Optional;
import java.util.UUID;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, UUID> {
    Optional<SeatReservation> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SeatReservation r join fetch r.flight where r.bookingId = :bookingId")
    Optional<SeatReservation> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
}
