package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;

public interface ApprovalActionHandler {
    boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType);

    String execute(ApprovalRequest request);
}
