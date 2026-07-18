package com.toir.repository.integration;

import com.toir.entity.integration.AtilEquipmentStatusOutboxEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtilEquipmentStatusOutboxRepository extends JpaRepository<AtilEquipmentStatusOutboxEvent, UUID> {
    boolean existsByHistoryId(UUID historyId);
    List<AtilEquipmentStatusOutboxEvent> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, Instant nextAttemptAt);
}
