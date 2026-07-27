package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;

import java.util.List;
import java.util.Optional;

public interface ApprovalRouteResolver {
    List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request);

    default ApprovalRouteSnapshot resolveRouteSnapshot(ApprovalRequest request) {
        return ApprovalRouteSnapshot.sequential(resolveRoute(request));
    }

    LifecycleRouteResolution resolveLifecycleRoute(
            ApprovalTargetType targetType,
            ApprovalActionType actionType);

    default Optional<CreateApprovalRequest.StepInput> resolveRole(String approverRole) {
        return Optional.empty();
    }
}
