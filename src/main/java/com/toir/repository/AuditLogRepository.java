package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;


@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    java.util.Optional<AuditLog> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<AuditLog> findAllByIsDeletedFalse();

    java.util.List<AuditLog> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM audit_logs WHERE is_deleted = false ORDER BY updated_at DESC",
            countQuery = "SELECT COUNT(*) FROM audit_logs WHERE is_deleted = false",
            nativeQuery = true)
    Page<AuditLog> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
}
