package com.toir.repository;

import com.toir.entity.AuditLog;
import com.toir.enums.AuditAction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    @Query(value = "SELECT * FROM audit_logs WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<AuditLog> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM audit_logs WHERE is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<AuditLog> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    @Query(value = "SELECT * FROM audit_logs WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<AuditLog> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM audit_logs WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM audit_logs WHERE is_deleted = false", nativeQuery = true)
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
    Page<AuditLog> findAllByIsDeletedFalseOrderByCreatedAtDesc(@Param("action") String action, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, @Param("searchPattern") String searchPattern, @Param("userId") UUID userId, @Param("pageable") Pageable pageable);
}
