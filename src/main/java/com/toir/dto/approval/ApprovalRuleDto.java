package com.toir.dto.approval;

import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;

import java.util.List;
import java.util.UUID;

public record ApprovalRuleDto(
        UUID id,
        ApprovalTargetType targetType,
        ApprovalActionType actionType,
        String documentName,
        int stepsCount,
        List<Step> steps,
        boolean active
) {
    public ApprovalRuleDto(
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            String documentName,
            int stepsCount,
            List<Step> steps,
            boolean active
    ) {
        this(null, targetType, actionType, documentName, stepsCount, steps, active);
    }

    public record Step(
            int order,
            UUID approverId,
            String approverName,
            String approverRole,
            ApproverType approverType
    ) {
    }

    public enum ApproverType {
        USER,
        ROLE
    }
}
