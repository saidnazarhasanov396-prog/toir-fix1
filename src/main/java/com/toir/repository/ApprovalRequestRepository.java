package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    @Query(value = "SELECT * FROM approval_requests WHERE document_type = :documentType AND document_id = cast(:documentId as uuid) AND is_deleted = false", nativeQuery = true)
    List<ApprovalRequest> findAllByDocumentTypeAndDocumentId(@Param("documentType") String documentType, @Param("documentId") UUID documentId);

    @Query(value = "SELECT * FROM approval_requests WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByStatusOrderByCreatedAtDesc(@Param("status") ApprovalStatus status);

    @Query(value = "SELECT * FROM approval_requests WHERE requester_id = cast(:requesterId as uuid) AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByRequesterIdOrderByCreatedAtDesc(@Param("requesterId") UUID requesterId);
}
