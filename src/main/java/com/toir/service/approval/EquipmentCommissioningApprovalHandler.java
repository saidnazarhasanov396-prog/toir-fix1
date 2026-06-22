package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.equipment.EquipmentCommissioningActService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EquipmentCommissioningApprovalHandler implements ApprovalActionHandler {

    private final EquipmentCommissioningActService service;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.EQUIPMENT_COMMISSIONING
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        UUID actorId = request.getSteps().stream()
                .filter(step -> step.getDecidedById() != null)
                .reduce((first, second) -> second)
                .map(step -> step.getDecidedById())
                .orElse(null);
        if (request.getActionType() == ApprovalActionType.APPROVE) {
            service.finalizeApproval(targetId, actorId);
            return "{\"status\":\"APPROVED\"}";
        }
        String reason = request.getSteps().stream()
                .filter(step -> step.getComment() != null && !step.getComment().isBlank())
                .reduce((first, second) -> second)
                .map(step -> step.getComment())
                .orElse("Rejected by approval workflow");
        service.finalizeRejection(targetId, reason);
        return "{\"status\":\"REJECTED\"}";
    }
}
