package com.toir.repository;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    @Query(value = "SELECT * FROM approval_requests WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ApprovalRequest> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM approval_requests WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM approval_requests WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ApprovalRequest> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM approval_requests WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM approval_requests WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM approval_requests WHERE document_type = :documentType AND document_id = cast(:documentId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse(@Param("documentType") String documentType, @Param("documentId") UUID documentId);

    @Query(value = "SELECT * FROM approval_requests WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(@Param("status") ApprovalStatus status);

    @Query(value = "SELECT * FROM approval_requests WHERE requester_id = cast(:requesterId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByRequesterIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("requesterId") UUID requesterId);
}
