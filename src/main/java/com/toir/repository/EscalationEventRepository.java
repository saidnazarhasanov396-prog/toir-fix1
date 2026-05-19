package com.toir.repository;

import com.toir.entity.EscalationEvent;
import com.toir.enums.EscalationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EscalationEventRepository extends JpaRepository<EscalationEvent, UUID> {
    @Query(value = "SELECT * FROM escalation_events WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EscalationEvent> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM escalation_events WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EscalationEvent> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM escalation_events WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EscalationEvent> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM escalation_events WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM escalation_events WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM escalation_events WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EscalationEvent> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("status") EscalationStatus status);

    @Query(value = "SELECT * FROM escalation_events WHERE entity_type = cast(:entityType as varchar) AND entity_id = cast(:entityId as varchar) AND is_deleted = false", nativeQuery = true)
    List<EscalationEvent> findAllByEntityTypeAndEntityIdAndIsDeletedFalse(@Param("entityType") String entityType, @Param("entityId") String entityId);

    @Query(value = "SELECT COUNT(*) FROM escalation_events WHERE status = cast(:status as varchar) AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") EscalationStatus status);
}
