package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;

import java.util.List;
import java.util.UUID;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;

public record LifecycleRouteResolution(
        List<CreateApprovalRequest.StepInput> steps,
        LifecycleApprovalRoutePolicy.Reason reason,
        ApprovalFlowType flowType,
        ApprovalRejectionPolicy rejectionPolicy,
        UUID templateId,
        Long templateVersion
) {
    public LifecycleRouteResolution(
            List<CreateApprovalRequest.StepInput> steps,
            LifecycleApprovalRoutePolicy.Reason reason
    ) {
        this(steps, reason, ApprovalFlowType.SEQUENTIAL,
                ApprovalRejectionPolicy.TERMINATE, null, null);
    }

    public LifecycleRouteResolution(
            List<CreateApprovalRequest.StepInput> steps,
            LifecycleApprovalRoutePolicy.Reason reason,
            ApprovalFlowType flowType,
            UUID templateId,
            Long templateVersion
    ) {
        this(steps, reason, flowType, ApprovalRejectionPolicy.TERMINATE, templateId, templateVersion);
    }

    public LifecycleRouteResolution {
        steps = steps == null ? List.of() : List.copyOf(steps);
        flowType = flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
        rejectionPolicy = rejectionPolicy == null
                ? ApprovalRejectionPolicy.TERMINATE
                : rejectionPolicy;
    }

    public boolean resolved() {
        return reason == LifecycleApprovalRoutePolicy.Reason.VALID;
    }
}
