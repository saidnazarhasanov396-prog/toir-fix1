package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartDueEventWorkOrderLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SparePartDueEventWorkOrderLinkRepository
        extends JpaRepository<SparePartDueEventWorkOrderLink, UUID> {

    Optional<SparePartDueEventWorkOrderLink> findByDueEventIdAndIdempotencyKeyAndIsDeletedFalse(
            UUID dueEventId, String idempotencyKey);

    List<SparePartDueEventWorkOrderLink> findAllByDueEventIdInAndIsDeletedFalseOrderByCreatedAtDesc(
            List<UUID> dueEventIds);
}
