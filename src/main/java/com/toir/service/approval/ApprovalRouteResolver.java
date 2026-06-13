package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;

import java.util.List;

public interface ApprovalRouteResolver {
    List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request);
}
