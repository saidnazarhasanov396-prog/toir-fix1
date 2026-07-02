package com.toir.service.finance;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcurementBudgetAllocationService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final BudgetEventRepository budgetEventRepository;
    private final BudgetCommitmentService budgetCommitmentService;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;

    @Transactional
    public ProcurementRequestDto allocateBudget(UUID requestId, UUID budgetLineId,
                                                UUID actorUserId, String comment) {
        ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + requestId));

        assertCanMutate(request);
        assertCanAccessBudget(request);

        if (request.getBudgetAllocationStatus() == BudgetAllocationStatus.ALLOCATED) {
            throw RestException.badRequest("Procurement request is already allocated");
        }

        BudgetLine line = budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)
                .orElseThrow(() -> RestException.notFound("Budget line not found: " + budgetLineId));

        validateBudgetLineForProcurement(line, request);
        budgetCommitmentService.assertCanCommit(line, request.getTotalEstimatedCost());

        UUID oldBudgetLineId = request.getBudgetLineId();
        request.setBudgetLineId(budgetLineId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.ALLOCATED);
        request.setBudgetAllocatedAt(Instant.now());
        request.setBudgetAllocatedById(actorUserId);

        ProcurementRequest saved = procurementRequestRepository.save(request);

        recordBudgetAllocationEvent(line, requestId, oldBudgetLineId, budgetLineId, actorUserId,
                "PROCUREMENT_BUDGET_ALLOCATED", comment);
        auditBuilderService.log("procurement_request", requestId.toString(), AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST, "Budget allocated", null, saved);

        return ProcurementRequestDto.from(saved);
    }

    @Transactional
    public ProcurementRequestDto unallocateBudget(UUID requestId, UUID actorUserId, String comment) {
        ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + requestId));

        assertCanAccessBudget(request);

        if (request.getBudgetAllocationStatus() != BudgetAllocationStatus.ALLOCATED) {
            throw RestException.badRequest("Procurement request is not allocated");
        }

        if (request.getStatus() == ProcurementRequestStatus.APPROVED) {
            throw RestException.badRequest("Cannot unallocate approved procurement request");
        }

        assertCanMutate(request);

        UUID oldBudgetLineId = request.getBudgetLineId();
        BudgetLine line = oldBudgetLineId == null
                ? null
                : budgetLineRepository.findByIdAndIsDeletedFalse(oldBudgetLineId).orElse(null);

        request.setBudgetLineId(null);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.UNALLOCATED);
        request.setBudgetAllocatedAt(null);
        request.setBudgetAllocatedById(null);

        ProcurementRequest saved = procurementRequestRepository.save(request);

        if (line != null) {
            recordBudgetAllocationEvent(line, requestId, oldBudgetLineId, null, actorUserId,
                    "PROCUREMENT_BUDGET_UNALLOCATED", comment);
        }
        auditBuilderService.log("procurement_request", requestId.toString(), AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST, "Budget unallocated", null, saved);

        return ProcurementRequestDto.from(saved);
    }

    private void validateBudgetLineForProcurement(BudgetLine line, ProcurementRequest request) {
        MaintenanceBudget budget = line.getBudget();
        if (budget == null) {
            throw RestException.badRequest("Budget line has no budget");
        }

        if (request.getDepartmentId() != null && budget.getDepartmentId() != null
                && !request.getDepartmentId().equals(budget.getDepartmentId())) {
            throw RestException.badRequest("Budget line department does not match procurement request department");
        }

        if (budget.getStatus() != BudgetStatus.APPROVED && budget.getStatus() != BudgetStatus.LOCKED) {
            throw RestException.badRequest("Budget must be APPROVED or LOCKED");
        }
    }

    private void assertCanMutate(ProcurementRequest request) {
        if (request.getStatus() != ProcurementRequestStatus.DRAFT
                && request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Can only allocate budget for DRAFT or SUBMITTED requests");
        }
    }

    private void assertCanAccessBudget(ProcurementRequest request) {
        if (!scopeAccessService.isScopeAdmin() && request.getDepartmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.getDepartmentId());
        }
    }

    private void recordBudgetAllocationEvent(BudgetLine line, UUID procurementRequestId,
                                             UUID oldBudgetLineId, UUID newBudgetLineId,
                                             UUID actorUserId, String eventType, String comment) {
        BudgetEvent event = new BudgetEvent();
        event.setBudgetId(line.getBudget().getId());
        event.setBudgetLineId(line.getId());
        event.setEventType(eventType);
        event.setOldValues("{\"budgetLineId\":"
                + (oldBudgetLineId == null ? "null" : "\"" + oldBudgetLineId + "\"")
                + ",\"procurementRequestId\":\"" + procurementRequestId + "\"}");
        event.setNewValues("{\"budgetLineId\":"
                + (newBudgetLineId == null ? "null" : "\"" + newBudgetLineId + "\"")
                + ",\"procurementRequestId\":\"" + procurementRequestId + "\"}");
        event.setActorUserId(actorUserId);
        event.setComment(comment == null || comment.isBlank()
                ? "Procurement budget allocation change"
                : comment.trim());
        event.setOccurredAt(Instant.now());
        budgetEventRepository.save(event);
    }
}
