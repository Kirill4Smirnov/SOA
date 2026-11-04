package ru.soa.flight.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.soa.flight.domain.Flight;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlightRepository extends JpaRepository<Flight, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flight f where f.id = :id")
    Optional<Flight> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select f from Flight f
            where f.origin.code = :origin and f.destination.code = :destination
              and f.status = ru.soa.flight.domain.FlightStatus.SCHEDULED
            order by f.departureTime
            """)
    List<Flight> search(@Param("origin") String origin, @Param("destination") String destination);

    @Query("""
            select f from Flight f
            where f.origin.code = :origin and f.destination.code = :destination
              and f.status = ru.soa.flight.domain.FlightStatus.SCHEDULED
              and f.departureTime >= :from and f.departureTime < :to
            order by f.departureTime
            """)
    List<Flight> searchByDate(@Param("origin") String origin, @Param("destination") String destination,
                              @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}

