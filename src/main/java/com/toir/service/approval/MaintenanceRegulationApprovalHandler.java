package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.maintanance.MaintenanceRegulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenanceRegulationApprovalHandler implements ApprovalActionHandler {

    private final MaintenanceRegulationService maintenanceRegulationService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.MAINTENANCE_REGULATION
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        if (request.getActionType() == ApprovalActionType.REJECT) {
            maintenanceRegulationService.finalizeRejectionFromApprovalRequest(targetId);
            return "{\"status\":\"REJECTED\"}";
        }
        maintenanceRegulationService.finalizeApprovalFromApprovalRequest(targetId);
        return "{\"status\":\"APPROVED\"}";
    }
}
