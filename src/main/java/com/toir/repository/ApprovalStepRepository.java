package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.enums.ApprovalDecision;
import com.toir.entity.ApprovalStep;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    java.util.Optional<ApprovalStep> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ApprovalStep> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<ApprovalStep> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM approval_steps WHERE request_id = :requestId AND is_deleted = false ORDER BY step_number ASC", nativeQuery = true)
    List<ApprovalStep> findAllByRequestIdAndIsDeletedFalseOrderByStepNumberAsc(@Param("requestId") UUID requestId);

    @Query(value = "SELECT * FROM approval_steps WHERE approver_id = :approverId AND decision = :decision AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ApprovalStep> findAllByApproverIdAndDecisionAndIsDeletedFalse(@Param("approverId") UUID approverId, @Param("decision") ApprovalDecision decision);
}
