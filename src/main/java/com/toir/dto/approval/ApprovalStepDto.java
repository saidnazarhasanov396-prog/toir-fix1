package com.toir.dto.approval;

import com.toir.enums.ApprovalDecision;
import com.toir.entity.ApprovalStep;

import java.time.Instant;
import java.util.UUID;

public record ApprovalStepDto(
        UUID id,
        int stepNumber,
        UUID approverId,
        String approverRole,
        ApprovalDecision decision,
        Instant decidedAt,
        String comment,
        UUID decidedById,
        String decidedByName,
        UUID delegatedForId,
        String delegatedForName,
        int approvalRound,
        String approverName
) {
    public ApprovalStepDto(UUID id,
                           int stepNumber,
                           UUID approverId,
                           String approverRole,
                           ApprovalDecision decision,
                           Instant decidedAt,
                           String comment) {
        this(id, stepNumber, approverId, approverRole, decision, decidedAt, comment,
                null, null, null, null, 1, null);
    }

    public ApprovalStepDto(UUID id,
                           int stepNumber,
                           UUID approverId,
                           String approverRole,
                           ApprovalDecision decision,
                           Instant decidedAt,
                           String comment,
                           UUID decidedById,
                           String decidedByName,
                           UUID delegatedForId,
                           String delegatedForName) {
        this(id, stepNumber, approverId, approverRole, decision, decidedAt, comment,
                decidedById, decidedByName, delegatedForId, delegatedForName, 1, null);
    }

    public static ApprovalStepDto from(ApprovalStep s) {
        return new ApprovalStepDto(
                s.getId(), s.getStepNumber(), s.getApproverId(), s.getApproverRole(),
                s.getDecision(), s.getDecidedAt(), s.getComment(),
                s.getDecidedById(), null, s.getDelegatedForId(), null, s.getApprovalRound(), null);
    }
}
