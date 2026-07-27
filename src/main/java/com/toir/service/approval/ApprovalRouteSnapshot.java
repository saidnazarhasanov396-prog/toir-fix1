package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.enums.ApprovalFlowType;

import java.util.List;
import java.util.UUID;

public record ApprovalRouteSnapshot(
        ApprovalFlowType flowType,
        UUID templateId,
        Long templateVersion,
        List<CreateApprovalRequest.StepInput> steps
) {
    public ApprovalRouteSnapshot {
        flowType = flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static ApprovalRouteSnapshot sequential(List<CreateApprovalRequest.StepInput> steps) {
        return new ApprovalRouteSnapshot(ApprovalFlowType.SEQUENTIAL, null, null, steps);
    }
}
