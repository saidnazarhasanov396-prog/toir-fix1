package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.planning.PprPlanningApprovalBindingService;
import com.toir.service.planning.PprPlanningMaterializationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PprPlanningSessionApprovalHandler implements ApprovalActionHandler {

    private final PprPlanningApprovalBindingService bindingService;
    private final PprPlanningMaterializationService materializationService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.PPR_PLANNING_SESSION
                && (actionType == ApprovalActionType.APPROVE
                || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        if (request.getActionType() == ApprovalActionType.REJECT) {
            bindingService.markRejected(request);
            return "{\"status\":\"REJECTED\"}";
        }
        UUID approverId = request.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                .map(step -> step.getDecidedById() == null
                        ? step.getApproverId() : step.getDecidedById())
                .reduce((first, second) -> second)
                .orElse(null);
        var plan = materializationService.materialize(request, approverId);
        return "{\"status\":\"APPROVED\",\"planId\":\"" + plan.getId() + "\"}";
    }
}
