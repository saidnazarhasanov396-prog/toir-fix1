package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;

import java.util.List;
import java.util.UUID;
import com.toir.enums.ApprovalFlowType;

public record LifecycleRouteResolution(
        List<CreateApprovalRequest.StepInput> steps,
        LifecycleApprovalRoutePolicy.Reason reason,
        ApprovalFlowType flowType,
        UUID templateId,
        Long templateVersion
) {
    public LifecycleRouteResolution(
            List<CreateApprovalRequest.StepInput> steps,
            LifecycleApprovalRoutePolicy.Reason reason
    ) {
        this(steps, reason, ApprovalFlowType.SEQUENTIAL, null, null);
    }

    public LifecycleRouteResolution {
        steps = steps == null ? List.of() : List.copyOf(steps);
        flowType = flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
    }

    public boolean resolved() {
        return reason == LifecycleApprovalRoutePolicy.Reason.VALID;
    }
}
