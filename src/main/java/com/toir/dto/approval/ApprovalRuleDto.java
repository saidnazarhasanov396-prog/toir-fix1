package com.toir.dto.approval;

import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
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
        boolean active,
        ApprovalFlowType flowType,
        ApprovalRejectionPolicy rejectionPolicy,
        Long version
) {
    public ApprovalRuleDto(
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            String documentName,
            int stepsCount,
            List<Step> steps,
            boolean active,
            ApprovalFlowType flowType,
            ApprovalRejectionPolicy rejectionPolicy,
            Long version
    ) {
        this(null, targetType, actionType, documentName, stepsCount, steps, active,
                flowType, rejectionPolicy, version);
    }

    public ApprovalRuleDto(
            UUID id,
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            String documentName,
            int stepsCount,
            List<Step> steps,
            boolean active
    ) {
        this(id, targetType, actionType, documentName, stepsCount, steps, active,
                ApprovalFlowType.SEQUENTIAL, ApprovalRejectionPolicy.TERMINATE, null);
    }

    public ApprovalRuleDto(
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            String documentName,
            int stepsCount,
            List<Step> steps,
            boolean active
    ) {
        this(null, targetType, actionType, documentName, stepsCount, steps, active,
                ApprovalFlowType.SEQUENTIAL, ApprovalRejectionPolicy.TERMINATE, null);
    }

    public ApprovalRuleDto(
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            String documentName,
            int stepsCount,
            List<Step> steps,
            boolean active,
            ApprovalFlowType flowType,
            Long version
    ) {
        this(null, targetType, actionType, documentName, stepsCount, steps, active,
                flowType, ApprovalRejectionPolicy.TERMINATE, version);
    }

    public ApprovalRuleDto(
            UUID id,
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            String documentName,
            int stepsCount,
            List<Step> steps,
            boolean active,
            ApprovalFlowType flowType,
            Long version
    ) {
        this(id, targetType, actionType, documentName, stepsCount, steps, active,
                flowType, ApprovalRejectionPolicy.TERMINATE, version);
    }

    public ApprovalRuleDto {
        flowType = flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
        rejectionPolicy = rejectionPolicy == null
                ? ApprovalRejectionPolicy.TERMINATE
                : rejectionPolicy;
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
