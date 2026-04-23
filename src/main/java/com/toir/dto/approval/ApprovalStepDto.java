package com.toir.dto.approval;

import com.toir.entity.ApprovalDecision;
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
        String comment
) {
    public static ApprovalStepDto from(ApprovalStep s) {
        return new ApprovalStepDto(
                s.getId(), s.getStepNumber(), s.getApproverId(), s.getApproverRole(),
                s.getDecision(), s.getDecidedAt(), s.getComment());
    }
}
