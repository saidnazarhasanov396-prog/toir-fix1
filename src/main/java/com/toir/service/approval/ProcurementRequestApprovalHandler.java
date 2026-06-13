package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProcurementRequestApprovalHandler implements ApprovalActionHandler {

    private final ProcurementRequestRepository procurementRequestRepository;

    @Override
    public boolean supports(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType == ApprovalTargetType.PROCUREMENT_REQUEST
                && (actionType == ApprovalActionType.APPROVE || actionType == ApprovalActionType.REJECT);
    }

    @Override
    public String execute(ApprovalRequest approval) {
        UUID targetId = targetId(approval);
        ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(targetId)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + targetId));
        if (approval.getActionType() == ApprovalActionType.APPROVE) {
            approve(request);
            return "{\"status\":\"APPROVED\"}";
        }
        reject(request, terminalComment(approval));
        return "{\"status\":\"REJECTED\"}";
    }

    private void approve(ProcurementRequest request) {
        if (request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Procurement request approval can be finalized only from SUBMITTED status");
        }
        request.setStatus(ProcurementRequestStatus.APPROVED);
        request.setApprovedAt(Instant.now());
        request.setRejectionReason(null);
        procurementRequestRepository.save(request);
    }

    private void reject(ProcurementRequest request, String comment) {
        if (request.getStatus() == ProcurementRequestStatus.RECEIVED
                || request.getStatus() == ProcurementRequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot reject completed procurement request");
        }
        request.setStatus(ProcurementRequestStatus.REJECTED);
        request.setRejectionReason(StringUtils.hasText(comment) ? comment.trim() : "Rejected by approval workflow");
        procurementRequestRepository.save(request);
    }

    private String terminalComment(ApprovalRequest approval) {
        return approval.getSteps().stream()
                .filter(step -> step.getDecision() == ApprovalDecision.REJECTED)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(ApprovalStep::getComment)
                .orElse(null);
    }

    private UUID targetId(ApprovalRequest approval) {
        return approval.getTargetId() == null ? approval.getDocumentId() : approval.getTargetId();
    }
}
