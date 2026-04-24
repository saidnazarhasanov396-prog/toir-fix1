package com.toir.repository;
import com.toir.entity.EscalationEvent;
import com.toir.enums.EscalationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EscalationEventRepository extends JpaRepository<EscalationEvent, UUID> {
    List<EscalationEvent> findAllByStatusOrderByRaisedAtDesc(EscalationStatus status);
    List<EscalationEvent> findAllByEntityTypeAndEntityId(String entityType, String entityId);
    long countByStatus(EscalationStatus status);
}
