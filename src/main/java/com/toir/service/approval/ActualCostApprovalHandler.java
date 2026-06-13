package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.ActualCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActualCostApprovalHandler implements ApprovalActionHandler {

    private final ActualCostService actualCostService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.ACTUAL_COST
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        UUID reviewerId = terminalActor(request);
        UUID targetId = targetId(request);
        if (request.getActionType() == ApprovalActionType.REJECT) {
            actualCostService.review(targetId, false, reviewerId,
                    terminalComment(request, "Rejected by approval workflow"));
            return "{\"status\":\"REJECTED\"}";
        }
        actualCostService.review(targetId, true, reviewerId,
                terminalComment(request, "Approved by approval workflow"));
        return "{\"status\":\"APPROVED\"}";
    }

    private UUID targetId(ApprovalRequest request) {
        return request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
    }

    private UUID terminalActor(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() != ApprovalDecision.PENDING)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(step -> step.getDecidedById() == null ? step.getApproverId() : step.getDecidedById())
                .orElse(null);
    }

    private String terminalComment(ApprovalRequest request, String fallback) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() != ApprovalDecision.PENDING)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(ApprovalStep::getComment)
                .filter(comment -> comment != null && !comment.isBlank())
                .orElse(fallback);
    }
}
