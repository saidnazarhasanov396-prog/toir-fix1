package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenanceRegulationApprovalHandler implements ApprovalActionHandler {

    private final MaintenanceRegulationRepository maintenanceRegulationRepository;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.MAINTENANCE_REGULATION
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        maintenanceRegulationRepository.findByIdAndIsDeletedFalse(targetId)
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + targetId));
        if (request.getActionType() == ApprovalActionType.REJECT) {
            return "{\"status\":\"REJECTED\"}";
        }
        return "{\"status\":\"APPROVED\"}";
    }
}
