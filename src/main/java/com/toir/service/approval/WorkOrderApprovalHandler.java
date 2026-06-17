package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkOrderApprovalHandler implements ApprovalActionHandler {

    private final WorkOrderService workOrderService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.WORK_ORDER
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        if (request.getActionType() == ApprovalActionType.REJECT) {
            return "{\"status\":\"REJECTED\"}";
        }
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        UUID approverId = request.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                .map(step -> step.getDecidedById() == null ? step.getApproverId() : step.getDecidedById())
                .reduce((first, second) -> second)
                .orElse(null);
        workOrderService.approve(targetId, approverId);
        return "{\"status\":\"APPROVED\"}";
    }
}
