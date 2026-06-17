package com.toir.service.approval;

import com.toir.dto.regulationchangeproposal.RegulationChangeProposalReviewRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.RegulationChangeProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RegulationChangeProposalApprovalHandler implements ApprovalActionHandler {

    private final RegulationChangeProposalService proposalService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.REGULATION_CHANGE_PROPOSAL
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        RegulationChangeProposalReviewRequest review =
                new RegulationChangeProposalReviewRequest(terminalComment(request));
        if (request.getActionType() == ApprovalActionType.REJECT) {
            proposalService.finalizeRejectionFromApprovalRequest(targetId(request), review);
            return "{\"status\":\"REJECTED\"}";
        }
        proposalService.finalizeApprovalFromApprovalRequest(targetId(request), review);
        return "{\"status\":\"APPROVED\"}";
    }

    private UUID targetId(ApprovalRequest request) {
        return request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
    }

    private String terminalComment(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() != ApprovalDecision.PENDING)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(ApprovalStep::getComment)
                .orElse(null);
    }
}
