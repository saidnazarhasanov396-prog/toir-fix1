package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EscalationEvent;
import com.toir.enums.EscalationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EscalationEventRepository extends JpaRepository<EscalationEvent, UUID> {
    @Query(value = "SELECT * FROM escalation_events WHERE status = :status AND is_deleted = false ORDER BY raised_at DESC", nativeQuery = true)
    List<EscalationEvent> findAllByStatusOrderByRaisedAtDesc(@Param("status") EscalationStatus status);

    @Query(value = "SELECT * FROM escalation_events WHERE entity_type = :entityType AND entity_id = :entityId AND is_deleted = false", nativeQuery = true)
    List<EscalationEvent> findAllByEntityTypeAndEntityId(@Param("entityType") String entityType, @Param("entityId") String entityId);

    @Query(value = "SELECT COUNT(*) FROM escalation_events WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") EscalationStatus status);
}
