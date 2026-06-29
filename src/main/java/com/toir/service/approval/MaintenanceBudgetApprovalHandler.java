package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenanceBudgetApprovalHandler implements ApprovalActionHandler {

    private final MaintenanceBudgetRepository maintenanceBudgetRepository;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.MAINTENANCE_BUDGET
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest approval) {
        if (approval.getActionType() == ApprovalActionType.REJECT) {
            return "{\"status\":\"REJECTED\"}";
        }
        UUID targetId = approval.getTargetId() == null ? approval.getDocumentId() : approval.getTargetId();
        MaintenanceBudget budget = maintenanceBudgetRepository.findByIdAndIsDeletedFalse(targetId)
                .orElseThrow(() -> RestException.notFound("Budget not found: " + targetId));
        if (budget.getStatus() != BudgetStatus.SUBMITTED) {
            throw RestException.badRequest("Budget approval request can be finalized only from SUBMITTED status");
        }
        budget.setStatus(BudgetStatus.APPROVED);
        maintenanceBudgetRepository.save(budget);
        return "{\"status\":\"APPROVED\"}";
    }
}
