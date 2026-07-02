package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.service.finance.BudgetCommitmentService;
import com.toir.service.warehouse.WarehouseTaskGenerationService;
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
    private final WarehouseTaskGenerationService taskGenerationService;
    private final BudgetCommitmentService budgetCommitmentService;

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
            approve(request, approval);
            return "{\"status\":\"APPROVED\"}";
        }
        reject(request, terminalComment(approval));
        return "{\"status\":\"REJECTED\"}";
    }

    private void approve(ProcurementRequest request, ApprovalRequest approval) {
        if (request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Procurement request approval can be finalized only from SUBMITTED status");
        }
        if (request.getBudgetAllocationStatus() != BudgetAllocationStatus.ALLOCATED
                || request.getBudgetLineId() == null) {
            throw RestException.badRequest(
                    "Procurement request must be allocated to a budget line before approval");
        }
        request.setStatus(ProcurementRequestStatus.APPROVED);
        request.setApprovedAt(Instant.now());
        request.setRejectionReason(null);
        ProcurementRequest saved = procurementRequestRepository.save(request);

        budgetCommitmentService.commitBudget(
                request.getBudgetLineId(),
                request.getTotalEstimatedCost(),
                "PROCUREMENT_REQUEST",
                request.getId(),
                terminalActor(approval),
                "Budget commitment on procurement approval"
        );

        taskGenerationService.generateReceiveForApprovedProcurement(saved);
    }

    private void reject(ProcurementRequest request, String comment) {
        if (request.getStatus() == ProcurementRequestStatus.RECEIVED
                || request.getStatus() == ProcurementRequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot reject completed procurement request");
        }

        if (request.getBudgetLineId() != null
                && request.getBudgetAllocationStatus() == BudgetAllocationStatus.ALLOCATED
                && request.getStatus() == ProcurementRequestStatus.APPROVED) {
            budgetCommitmentService.releaseBudget(
                    request.getBudgetLineId(),
                    request.getTotalEstimatedCost(),
                    "PROCUREMENT_REQUEST",
                    request.getId(),
                    null,
                    "Budget release on procurement rejection"
            );
        }

        request.setStatus(ProcurementRequestStatus.REJECTED);
        request.setRejectionReason(StringUtils.hasText(comment) ? comment.trim() : "Rejected by approval workflow");
        procurementRequestRepository.save(request);
    }

    private UUID terminalActor(ApprovalRequest approval) {
        return approval.getSteps().stream()
                .filter(step -> step.getDecision() != ApprovalDecision.PENDING)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber))
                .map(step -> step.getDecidedById() == null ? step.getApproverId() : step.getDecidedById())
                .orElse(null);
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
