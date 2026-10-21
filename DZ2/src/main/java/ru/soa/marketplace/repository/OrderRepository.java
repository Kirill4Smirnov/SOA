package ru.soa.marketplace.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.soa.marketplace.domain.Order;
import ru.soa.marketplace.domain.OrderStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "items.product", "promoCode"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "items.product", "promoCode"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findDetailedByIdForUpdate(@Param("id") UUID id);
}
