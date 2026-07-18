package com.toir.repository.integration;

import com.toir.entity.integration.ErpEmployeeClearanceOutboxEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpEmployeeClearanceOutboxRepository extends JpaRepository<ErpEmployeeClearanceOutboxEvent, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<ErpEmployeeClearanceOutboxEvent> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, Instant nextAttemptAt);
}
