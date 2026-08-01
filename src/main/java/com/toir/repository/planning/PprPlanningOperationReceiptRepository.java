package com.toir.repository.planning;

import com.toir.entity.planning.PprPlanningOperationReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PprPlanningOperationReceiptRepository
        extends JpaRepository<PprPlanningOperationReceipt, UUID> {

    Optional<PprPlanningOperationReceipt>
    findBySessionIdAndOperationTypeAndIdempotencyKeyAndIsDeletedFalse(
            UUID sessionId,
            String operationType,
            String idempotencyKey);
}
