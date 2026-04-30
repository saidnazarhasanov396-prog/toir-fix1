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
            "AND (COALESCE(?1::text, al.action::text) = al.action::text) " +
            "AND (?2 IS NULL OR CAST(al.created_at AS date) >= ?2) " +
            "AND (?3 IS NULL OR CAST(al.created_at AS date) <= ?3) " +
            "AND (?5 IS NULL OR al.user_id = ?5) " +
            "AND (?4 IS NULL OR al.message ILIKE ?4 OR al.entity_type ILIKE ?4) " +
            "ORDER BY al.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM audit_logs al WHERE al.is_deleted = false " +
            "AND (COALESCE(?1::text, al.action::text) = al.action::text) " +
            "AND (?2 IS NULL OR CAST(al.created_at AS date) >= ?2) " +
            "AND (?3 IS NULL OR CAST(al.created_at AS date) <= ?3) " +
            "AND (?5 IS NULL OR al.user_id = ?5) " +
            "AND (?4 IS NULL OR al.message ILIKE ?4 OR al.entity_type ILIKE ?4)",
            nativeQuery = true)
    Page<AuditLog> findAllByIsDeletedFalseOrderByCreatedAtDesc(
            @Param("1") AuditAction action,
            @Param("2") LocalDate fromDate,
            @Param("3") LocalDate toDate,
            @Param("4") String searchPattern,
            @Param("5") UUID userId,
            Pageable pageable
            );
}
