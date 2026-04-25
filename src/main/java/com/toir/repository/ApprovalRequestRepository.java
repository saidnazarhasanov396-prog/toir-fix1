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
    java.util.Optional<ApprovalRequest> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ApprovalRequest> findAllByIsDeletedFalse();

    java.util.List<ApprovalRequest> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM approval_requests WHERE document_type = :documentType AND document_id = cast(:documentId as uuid) AND is_deleted = false", nativeQuery = true)
    List<ApprovalRequest> findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse(@Param("documentType") String documentType, @Param("documentId") UUID documentId);

    @Query(value = "SELECT * FROM approval_requests WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(@Param("status") ApprovalStatus status);

    @Query(value = "SELECT * FROM approval_requests WHERE requester_id = cast(:requesterId as uuid) AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByRequesterIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("requesterId") UUID requesterId);
}
