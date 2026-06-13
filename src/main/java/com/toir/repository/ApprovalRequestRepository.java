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

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE document_type = :documentType
              AND document_id = cast(:documentId as uuid)
              AND status = cast(:status as varchar)
              AND is_deleted = false
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ApprovalRequest> findFirstByDocumentTypeAndDocumentIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            @Param("documentType") String documentType,
            @Param("documentId") UUID documentId,
            @Param("status") ApprovalStatus status
    );

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = cast(:status as varchar)
              AND (
                    (target_type = :targetType AND target_id = cast(:targetId as uuid))
                 OR (document_type = :targetType AND document_id = cast(:targetId as uuid))
              )
              AND (
                    action_type = :actionType
                 OR action_type IS NULL
              )
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ApprovalRequest> findFirstPendingByTargetAndAction(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("actionType") String actionType,
            @Param("status") ApprovalStatus status
    );

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = 'PENDING'
              AND expires_at IS NOT NULL
              AND expires_at < :now
            ORDER BY expires_at ASC
            """, nativeQuery = true)
    List<ApprovalRequest> findExpiredPending(@Param("now") java.time.Instant now);

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = 'PENDING'
              AND escalated_at IS NULL
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<ApprovalRequest> findPendingWithoutEscalation();

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = 'PENDING'
              AND expires_at IS NOT NULL
              AND expires_at < :now
            ORDER BY expires_at ASC
            """, nativeQuery = true)
    List<ApprovalRequest> findOverdue(@Param("now") java.time.Instant now);

    @Query(value = "SELECT COUNT(*) FROM approval_requests WHERE is_deleted = false AND status = cast(:status as varchar)", nativeQuery = true)
    long countByStatus(@Param("status") ApprovalStatus status);

    @Query(value = "SELECT COUNT(*) FROM approval_requests WHERE is_deleted = false AND escalated_at IS NOT NULL", nativeQuery = true)
    long countEscalated();
}
