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

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE COALESCE(target_type, document_type) = :targetType
              AND COALESCE(target_id, document_id) = cast(:targetId as uuid)
              AND is_deleted = false
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<ApprovalRequest> findAllByTargetTypeAndTargetIdAndIsDeletedFalse(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId
    );

    @Query(value = "SELECT * FROM approval_requests WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(@Param("status") ApprovalStatus status);

    @Query(value = "SELECT * FROM approval_requests WHERE requester_id = cast(:requesterId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalRequest> findAllByRequesterIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("requesterId") UUID requesterId);

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE COALESCE(target_type, document_type) = :targetType
              AND COALESCE(target_id, document_id) = cast(:targetId as uuid)
              AND status = cast(:status as varchar)
              AND is_deleted = false
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ApprovalRequest> findFirstByTargetTypeAndTargetIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("status") ApprovalStatus status
    );

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = :status
              AND COALESCE(target_type, document_type) = :targetType
              AND COALESCE(target_id, document_id) = cast(:targetId as uuid)
              AND COALESCE(action_type, 'APPROVE') = :actionType
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ApprovalRequest> findFirstPendingByTargetAndAction(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("actionType") String actionType,
            @Param("status") String status
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

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = :status
              AND COALESCE(target_type, document_type) = :targetType
              AND COALESCE(target_id, document_id) = cast(:targetId as uuid)
              AND COALESCE(action_type, 'APPROVE') = :actionType
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<ApprovalRequest> findAllPendingByTargetAndAction(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("actionType") String actionType,
            @Param("status") String status
    );

    @Query(value = """
            SELECT *
            FROM approval_requests
            WHERE is_deleted = false
              AND status = :status
              AND COALESCE(target_type, document_type) = :targetType
              AND COALESCE(target_id, document_id) = cast(:targetId as uuid)
              AND COALESCE(action_type, 'APPROVE') = :actionType
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<ApprovalRequest> findAllApprovedByTargetAndActionOrderByCreatedAtDescIdDesc(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("actionType") String actionType,
            @Param("status") String status
    );
}
