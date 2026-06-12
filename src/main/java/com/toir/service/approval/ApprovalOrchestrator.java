package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;

import java.util.UUID;

public interface ApprovalOrchestrator {
    ApprovalRequestDto requestApproval(CreateApprovalRequest request);

    ApprovalRequestDto approve(UUID approvalId, DecisionRequest decision);

    ApprovalRequestDto reject(UUID approvalId, DecisionRequest decision);
}
