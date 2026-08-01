package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalTieBreakPolicy;
import com.toir.enums.ApprovalTargetType;

import java.util.List;
import java.util.UUID;

public record LifecycleApprovalStartPlan(
        ApprovalTargetType targetType,
        UUID targetId,
        ApprovalActionType actionType,
        ApprovalRequest reusableRequest,
        List<CreateApprovalRequest.StepInput> frozenSteps,
        LifecycleApprovalRoutePolicy.Reason failure,
        ApprovalFlowType flowType,
        ApprovalRejectionPolicy rejectionPolicy,
        ApprovalTieBreakPolicy tieBreakPolicy,
        UUID templateId,
        Long templateVersion
) {
    public LifecycleApprovalStartPlan(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            ApprovalRequest reusableRequest,
            List<CreateApprovalRequest.StepInput> frozenSteps,
            LifecycleApprovalRoutePolicy.Reason failure
    ) {
        this(targetType, targetId, actionType, reusableRequest, frozenSteps, failure,
                ApprovalFlowType.SEQUENTIAL, ApprovalRejectionPolicy.TERMINATE, null, null, null);
    }

    public LifecycleApprovalStartPlan(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            ApprovalRequest reusableRequest,
            List<CreateApprovalRequest.StepInput> frozenSteps,
            LifecycleApprovalRoutePolicy.Reason failure,
            ApprovalFlowType flowType,
            UUID templateId,
            Long templateVersion
    ) {
        this(targetType, targetId, actionType, reusableRequest, frozenSteps, failure,
                flowType, ApprovalRejectionPolicy.TERMINATE, null, templateId, templateVersion);
    }

    public LifecycleApprovalStartPlan(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            ApprovalRequest reusableRequest,
            List<CreateApprovalRequest.StepInput> frozenSteps,
            LifecycleApprovalRoutePolicy.Reason failure,
            ApprovalFlowType flowType,
            ApprovalRejectionPolicy rejectionPolicy,
            UUID templateId,
            Long templateVersion
    ) {
        this(targetType, targetId, actionType, reusableRequest, frozenSteps, failure,
                flowType, rejectionPolicy, null, templateId, templateVersion);
    }

    public LifecycleApprovalStartPlan {
        frozenSteps = frozenSteps == null ? List.of() : List.copyOf(frozenSteps);
        flowType = flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
        rejectionPolicy = rejectionPolicy == null
                ? ApprovalRejectionPolicy.TERMINATE
                : rejectionPolicy;
    }

    public boolean reusable() {
        return reusableRequest != null;
    }

    public boolean creatable() {
        return failure == LifecycleApprovalRoutePolicy.Reason.VALID && !reusable();
    }
}
