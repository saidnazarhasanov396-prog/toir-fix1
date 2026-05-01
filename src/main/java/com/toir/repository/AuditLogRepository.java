package com.toir.repository;

import com.toir.enums.AuditAction;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.toir.entity.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.UUID;


@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    java.util.Optional<AuditLog> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<AuditLog> findAllByIsDeletedFalse();

    java.util.List<AuditLog> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM audit_logs al WHERE al.is_deleted = false " +
            "AND (CAST(:action AS text) IS NULL OR al.action = CAST(:action AS text)) " +
            "AND (CAST(:fromDate AS date) IS NULL OR CAST(al.created_at AS date) >= CAST(:fromDate AS date)) " +
            "AND (CAST(:toDate AS date) IS NULL OR CAST(al.created_at AS date) <= CAST(:toDate AS date)) " +
            "AND (CAST(:userId AS uuid) IS NULL OR al.user_id = CAST(:userId AS uuid)) " +
            "AND (CAST(:searchPattern AS text) IS NULL OR al.message ILIKE CAST(:searchPattern AS text) OR al.entity_type ILIKE CAST(:searchPattern AS text)) " +
            "ORDER BY al.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM audit_logs al WHERE al.is_deleted = false " +
            "AND (CAST(:action AS text) IS NULL OR al.action = CAST(:action AS text)) " +
            "AND (CAST(:fromDate AS date) IS NULL OR CAST(al.created_at AS date) >= CAST(:fromDate AS date)) " +
            "AND (CAST(:toDate AS date) IS NULL OR CAST(al.created_at AS date) <= CAST(:toDate AS date)) " +
            "AND (CAST(:userId AS uuid) IS NULL OR al.user_id = CAST(:userId AS uuid)) " +
            "AND (CAST(:searchPattern AS text) IS NULL OR al.message ILIKE CAST(:searchPattern AS text) OR al.entity_type ILIKE CAST(:searchPattern AS text))",
            nativeQuery = true)
    Page<AuditLog> findAllByIsDeletedFalseOrderByCreatedAtDesc(
            @Param("action") String action,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("searchPattern") String searchPattern,
            @Param("userId") UUID userId,
            Pageable pageable
            );
}
