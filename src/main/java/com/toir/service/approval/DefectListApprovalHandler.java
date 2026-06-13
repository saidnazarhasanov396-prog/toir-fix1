package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.defects.DefectListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefectListApprovalHandler implements ApprovalActionHandler {

    private final DefectListService defectListService;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.DEFECT_LIST
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest request) {
        if (request.getActionType() == ApprovalActionType.REJECT) {
            return "{\"status\":\"REJECTED\"}";
        }
        defectListService.approve(targetId(request), terminalActor(request));
        return "{\"status\":\"APPROVED\"}";
    }

    private UUID targetId(ApprovalRequest request) {
        return request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
    }

    private UUID terminalActor(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(step -> step.getDecidedById() == null ? step.getApproverId() : step.getDecidedById())
                .orElse(null);
    }
}
