package ru.soa.marketplace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.soa.marketplace.domain.OperationType;
import ru.soa.marketplace.domain.UserOperation;

import java.util.Optional;
import java.util.UUID;

public interface UserOperationRepository extends JpaRepository<UserOperation, UUID> {
    Optional<UserOperation> findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(
            UUID userId, OperationType operationType);
}
