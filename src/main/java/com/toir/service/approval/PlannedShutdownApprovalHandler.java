package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.PlannedShutdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlannedShutdownApprovalHandler implements ApprovalActionHandler {

    private final PlannedShutdownService plannedShutdownService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.PLANNED_SHUTDOWN
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        if (request.getActionType() == ApprovalActionType.REJECT) {
            return "{\"status\":\"REJECTED\"}";
        }
        plannedShutdownService.finalizeApprovalFromApprovalRequest(targetId(request), request);
        return "{\"status\":\"APPROVED\"}";
    }

    private UUID targetId(ApprovalRequest request) {
        return request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
    }
}
