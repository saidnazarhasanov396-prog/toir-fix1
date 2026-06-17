package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.repair.RepairRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RepairRequestApprovalHandler implements ApprovalActionHandler {

    private final RepairRequestService repairRequestService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.REPAIR_REQUEST
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID targetId = targetId(request);
        if (request.getActionType() == ApprovalActionType.REJECT) {
            repairRequestService.finalizeRejectionFromApprovalRequest(
                    targetId,
                    terminalComment(request, "Rejected by approval workflow"));
            return "{\"status\":\"REJECTED\"}";
        }
        repairRequestService.finalizeApprovalFromApprovalRequest(targetId);
        return "{\"status\":\"APPROVED\"}";
    }

    private UUID targetId(ApprovalRequest request) {
        return request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
    }

    private String terminalComment(ApprovalRequest request, String fallback) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.REJECTED)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(ApprovalStep::getComment)
                .filter(comment -> comment != null && !comment.isBlank())
                .orElse(fallback);
    }
}
