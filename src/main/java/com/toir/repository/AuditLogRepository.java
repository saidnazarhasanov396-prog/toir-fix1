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

    @Query(value = "SELECT * FROM audit_logs WHERE is_deleted = false " +
            "AND (:action IS NULL OR action = CAST(:action AS text)) " +
            "AND (:fromDate IS NULL OR created_at::date >= :fromDate) " +
            "AND (:toDate IS NULL OR created_at::date <= :toDate) " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:search IS NULL OR message ILIKE '%' || :search || '%' OR entity_type ILIKE '%' || :search || '%') " +
            "ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM audit_logs WHERE is_deleted = false " +
            "AND (:action IS NULL OR action = CAST(:action AS text)) " +
            "AND (:fromDate IS NULL OR created_at::date >= :fromDate) " +
            "AND (:toDate IS NULL OR created_at::date <= :toDate) " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:search IS NULL OR message ILIKE '%' || :search || '%' OR entity_type ILIKE '%' || :search || '%')",
            nativeQuery = true)
    Page<AuditLog> findAllByIsDeletedFalseOrderByCreatedAtDesc(
            Pageable pageable,
            @Param("action")  AuditAction action,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("search") String search,
            @Param("userId") UUID  userId
            );
}
