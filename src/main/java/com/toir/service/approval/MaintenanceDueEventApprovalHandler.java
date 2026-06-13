package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.maintanance.MaintenanceAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenanceDueEventApprovalHandler implements ApprovalActionHandler {

    private final MaintenanceAutomationService maintenanceAutomationService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.MAINTENANCE_DUE_EVENT
                && (actionType == ApprovalActionType.CREATE_TASK || actionType == ApprovalActionType.CREATE_WORK_ORDER);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        if (request.getStatus() == ApprovalStatus.REJECTED || request.getStatus() == ApprovalStatus.CANCELLED) {
            return maintenanceAutomationService.rejectDueEventApproval(targetId, terminalComment(request));
        }
        UUID approverId = request.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(step -> step.getDecidedById() == null ? step.getApproverId() : step.getDecidedById())
                .orElse(null);
        return maintenanceAutomationService.finalizeDueEventApproval(targetId, request.getActionType(), approverId);
    }

    private String terminalComment(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.REJECTED)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(ApprovalStep::getComment)
                .orElse(null);
    }
}
