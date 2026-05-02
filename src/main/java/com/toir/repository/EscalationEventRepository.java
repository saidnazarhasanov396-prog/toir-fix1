package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EscalationEvent;
import com.toir.enums.EscalationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface EscalationEventRepository extends JpaRepository<EscalationEvent, UUID> {
    java.util.Optional<EscalationEvent> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<EscalationEvent> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<EscalationEvent> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    List<EscalationEvent> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(EscalationStatus status);

    List<EscalationEvent> findAllByEntityTypeAndEntityIdAndIsDeletedFalse(String entityType, String entityId);

    long countByStatusAndIsDeletedFalse(EscalationStatus status);
}
