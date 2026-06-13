package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;

public interface ApprovalActionExecutor {
    String execute(ApprovalRequest request);
}
