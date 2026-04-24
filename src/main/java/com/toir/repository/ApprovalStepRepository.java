package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.enums.ApprovalDecision;
import com.toir.entity.ApprovalStep;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    List<ApprovalStep> findAllByRequestIdOrderByStepNumberAsc(UUID requestId);
    List<ApprovalStep> findAllByApproverIdAndDecision(UUID approverId, ApprovalDecision decision);
}
