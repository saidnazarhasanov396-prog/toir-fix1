package com.toir.repository.integration;

import com.toir.entity.integration.ErpEquipmentStatusOutboxEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpEquipmentStatusOutboxRepository extends JpaRepository<ErpEquipmentStatusOutboxEvent, UUID> {
    boolean existsByHistoryId(UUID historyId);

    List<ErpEquipmentStatusOutboxEvent> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, Instant nextAttemptAt);
}
