package com.toir.dto.approval;

import com.toir.entity.ApprovalHistory;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;

import java.time.Instant;
import java.util.UUID;

public record ApprovalHistoryDto(
        UUID id,
        UUID approvalId,
        ApprovalStatus oldStatus,
        ApprovalStatus newStatus,
        UUID changedBy,
        UUID delegatedForId,
        String comment,
        Instant changedAt,
        ApprovalActionType actionType,
        ApprovalTargetType targetType,
        UUID targetId,
        UUID stepId,
        Integer stepNumber,
        Integer approvalRound,
        ApprovalDecision decision
) {
    public static ApprovalHistoryDto from(ApprovalHistory history) {
        return new ApprovalHistoryDto(
                history.getId(),
                history.getApprovalId(),
                history.getOldStatus(),
                history.getNewStatus(),
                history.getChangedBy(),
                history.getDelegatedForId(),
                history.getComment(),
                history.getChangedAt(),
                history.getActionType(),
                history.getTargetType(),
                history.getTargetId(),
                history.getStepId(),
                history.getStepNumber(),
                history.getApprovalRound(),
                history.getDecision()
        );
    }
}
