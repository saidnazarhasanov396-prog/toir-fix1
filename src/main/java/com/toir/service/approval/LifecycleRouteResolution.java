package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;

import java.util.List;

public record LifecycleRouteResolution(
        List<CreateApprovalRequest.StepInput> steps,
        LifecycleApprovalRoutePolicy.Reason reason
) {
    public LifecycleRouteResolution {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public boolean resolved() {
        return reason == LifecycleApprovalRoutePolicy.Reason.VALID;
    }
}
