package com.toir.repository;

import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalDecision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    @Query(value = "SELECT * FROM approval_steps WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ApprovalStep> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM approval_steps WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalStep> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM approval_steps WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ApprovalStep> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM approval_steps WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM approval_steps WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM approval_steps WHERE request_id = :requestId AND is_deleted = false ORDER BY step_number ASC", nativeQuery = true)
    List<ApprovalStep> findAllByRequestIdAndIsDeletedFalseOrderByStepNumberAsc(@Param("requestId") UUID requestId);

    @Query(value = "SELECT * FROM approval_steps WHERE approver_id = :approverId AND decision = :decision AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalStep> findAllByApproverIdAndDecisionAndIsDeletedFalse(@Param("approverId") UUID approverId, @Param("decision") ApprovalDecision decision);
}
