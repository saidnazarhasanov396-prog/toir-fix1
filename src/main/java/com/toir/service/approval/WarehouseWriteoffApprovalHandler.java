package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.warehouse.WarehouseQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WarehouseWriteoffApprovalHandler implements ApprovalActionHandler {

    private final WarehouseQualityService warehouseQualityService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.WAREHOUSE_WRITEOFF
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        if (request.getActionType() == ApprovalActionType.REJECT) {
            warehouseQualityService.rejectFromApprovalWorkflow(targetId, terminalActor(request), terminalComment(request));
            return "{\"status\":\"REJECTED\"}";
        }
        warehouseQualityService.approveFromApprovalWorkflow(targetId, terminalActor(request), terminalComment(request));
        return "{\"status\":\"APPROVED\"}";
    }

    private UUID terminalActor(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() != ApprovalDecision.PENDING)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(step -> step.getDecidedById() == null ? step.getApproverId() : step.getDecidedById())
                .orElse(null);
    }

    private String terminalComment(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() != ApprovalDecision.PENDING)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(ApprovalStep::getComment)
                .orElse(null);
    }
}
