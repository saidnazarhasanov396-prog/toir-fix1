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
    @Query(value = "SELECT * FROM audit_logs ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM audit_logs",
            nativeQuery = true)
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
