package com.toir.service.finance;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.entity.projects.ActualCostAllocationEvent;
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
import com.toir.repository.actualCost.ActualCostAllocationEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcurementBudgetAllocationService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final ActualCostAllocationEventRepository allocationEventRepository;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> reviewQueue(UUID departmentId, ProcurementRequestStatus status, boolean unallocatedOnly) {
        UUID effectiveDepartmentId = scopeAccessService.enforceDepartmentScope(departmentId);
        BudgetAllocationStatus allocationStatus = unallocatedOnly ? BudgetAllocationStatus.UNALLOCATED : null;

        return procurementRequestRepository.findFinanceReviewQueue(effectiveDepartmentId, status, allocationStatus).stream()
                .map(ProcurementRequestDto::from)
                .toList();
    }

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

        UUID oldBudgetLineId = request.getBudgetLineId();
        request.setBudgetLineId(budgetLineId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.ALLOCATED);
        request.setBudgetAllocatedAt(Instant.now());
        request.setBudgetAllocatedById(actorUserId);

        ProcurementRequest saved = procurementRequestRepository.save(request);

        recordAllocationEvent(request.getId(), oldBudgetLineId, budgetLineId, actorUserId, comment);
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
        request.setBudgetLineId(null);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.UNALLOCATED);
        request.setBudgetAllocatedAt(null);
        request.setBudgetAllocatedById(null);

        ProcurementRequest saved = procurementRequestRepository.save(request);

        recordAllocationEvent(request.getId(), oldBudgetLineId, null, actorUserId, comment);
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

    private void recordAllocationEvent(UUID procurementRequestId, UUID oldBudgetLineId, UUID newBudgetLineId,
                                       UUID actorUserId, String comment) {
        ActualCostAllocationEvent event = new ActualCostAllocationEvent();
        event.setActualCostId(procurementRequestId);
        event.setOldBudgetLineId(oldBudgetLineId);
        event.setNewBudgetLineId(newBudgetLineId);
        event.setActorUserId(actorUserId);
        event.setComment(comment == null || comment.isBlank()
                ? "Procurement budget allocation change"
                : comment.trim());
        event.setOccurredAt(Instant.now());
        allocationEventRepository.save(event);
    }
}
