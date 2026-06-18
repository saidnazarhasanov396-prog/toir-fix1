package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;

import java.util.List;
import java.util.Optional;

public interface ApprovalRouteResolver {
    List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request);

    default Optional<CreateApprovalRequest.StepInput> resolveRole(String approverRole) {
        return Optional.empty();
    }
}
