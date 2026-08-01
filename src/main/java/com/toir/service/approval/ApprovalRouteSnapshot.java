package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalTieBreakPolicy;

import java.util.List;
import java.util.UUID;

public record ApprovalRouteSnapshot(
        ApprovalFlowType flowType,
        ApprovalRejectionPolicy rejectionPolicy,
        ApprovalTieBreakPolicy tieBreakPolicy,
        UUID templateId,
        Long templateVersion,
        List<CreateApprovalRequest.StepInput> steps
) {
    public ApprovalRouteSnapshot(
            ApprovalFlowType flowType,
            ApprovalRejectionPolicy rejectionPolicy,
            UUID templateId,
            Long templateVersion,
            List<CreateApprovalRequest.StepInput> steps
    ) {
        this(flowType, rejectionPolicy, null, templateId, templateVersion, steps);
    }

    public ApprovalRouteSnapshot(
            ApprovalFlowType flowType,
            UUID templateId,
            Long templateVersion,
            List<CreateApprovalRequest.StepInput> steps
    ) {
        this(flowType, ApprovalRejectionPolicy.TERMINATE, null, templateId, templateVersion, steps);
    }

    public ApprovalRouteSnapshot {
        flowType = flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
        rejectionPolicy = rejectionPolicy == null
                ? ApprovalRejectionPolicy.TERMINATE
                : rejectionPolicy;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static ApprovalRouteSnapshot sequential(List<CreateApprovalRequest.StepInput> steps) {
        return new ApprovalRouteSnapshot(ApprovalFlowType.SEQUENTIAL,
                ApprovalRejectionPolicy.TERMINATE, null, null, null, steps);
    }
}
