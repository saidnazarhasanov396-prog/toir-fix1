package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;

import java.util.List;
import java.util.UUID;

public record LifecycleApprovalStartPlan(
        ApprovalTargetType targetType,
        UUID targetId,
        ApprovalActionType actionType,
        ApprovalRequest reusableRequest,
        List<CreateApprovalRequest.StepInput> frozenSteps,
        LifecycleApprovalRoutePolicy.Reason failure
) {
    public LifecycleApprovalStartPlan {
        frozenSteps = frozenSteps == null ? List.of() : List.copyOf(frozenSteps);
    }

    public boolean reusable() {
        return reusableRequest != null;
    }

    public boolean creatable() {
        return failure == LifecycleApprovalRoutePolicy.Reason.VALID && !reusable();
    }
}
