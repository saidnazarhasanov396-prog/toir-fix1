package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;

public interface ApprovalActionExecutor {
    String executeApprovedAction(ApprovalRequest request);
}
