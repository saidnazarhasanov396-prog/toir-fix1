package com.toir.service;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ActualCostService {

    private static final Set<BudgetStatus> USABLE_BUDGET_STATUSES = EnumSet.of(BudgetStatus.APPROVED, BudgetStatus.LOCKED);
    private static final double EPSILON = 0.000001d;

    private final ActualCostRepository repository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final AuditBuilderService auditBuilderService;
    private final FinanceScopeService financeScopeService;
    private final NotificationService notificationService;
    private final RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @Transactional(readOnly = true)
    public List<ActualCostDto> findPending() {
        return financeScopeService
                .filterActualCosts(repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING))
                .stream()
                .map(ActualCostDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostDto> findByWorkOrder(UUID workOrderId) {
        return findByFilters(workOrderId, null);
    }

    @Transactional(readOnly = true)
    public List<ActualCostDto> findByFilters(UUID workOrderId, String search) {
        return financeScopeService.filterActualCosts(repository.findAllByFiltersOrderByUpdatedAtDesc(workOrderId, search)).stream()
                .map(ActualCostDto::from)
                .toList();
    }

    @Transactional
    public ActualCostDto create(ActualCostDto r) {
        WorkOrder workOrder = requireWorkOrderIfPresent(r.workOrderId());
        RepairRequest repairRequest = requireRepairRequestIfPresent(r.repairRequestId());
        ContractorWork contractorWork = requireContractorWorkIfPresent(r.contractorWorkId());
        WorkOrder effectiveWorkOrder = resolveEffectiveWorkOrder(workOrder, contractorWork);
        UUID effectiveBudgetLineId = r.budgetLineId() != null
                ? r.budgetLineId()
                : repairCampaignBudgetLineResolver.resolveForWorkOrder(effectiveWorkOrder);
        BudgetLine budgetLine = requireBudgetLineIfPresent(effectiveBudgetLineId);

        assertCanCreateActualCost(r, effectiveWorkOrder, repairRequest, budgetLine);

        ActualCost c = new ActualCost();
        c.setWorkOrderId(r.workOrderId());
        c.setRepairRequestId(r.repairRequestId());
        c.setContractorWorkId(r.contractorWorkId());
        c.setSourceType(resolveSourceType(r));
        c.setSourceId(resolveSourceId(r));
        c.setBudgetLineId(effectiveBudgetLineId);
        c.setCostCategoryId(r.costCategoryId());
        c.setAmount(r.amount());
        c.setNotes(r.notes());
        c.setStatus(ActualCostStatus.PENDING);
        financeScopeService.assertCanMutateActualCost(c);
        ActualCost saved = repository.save(c);

        auditBuilderService.log(
                "actual_cost",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.ACTUAL_COST,
                "Фактическая стоимость создана",
                null,
                saved
        );
        notificationService.notifyDepartmentByPermission(
                resolveActualCostDepartment(effectiveWorkOrder, repairRequest, budgetLine),
                com.toir.security.PermissionConstants.ACTUAL_COST_APPROVE,
                "Actual cost pending review",
                "Actual cost " + saved.getId() + " requires finance review.",
                NotificationSeverity.INFO,
                "ActualCost",
                saved.getId().toString()
        );

        return ActualCostDto.from(saved);
    }

    @Transactional
    public ActualCostDto review(UUID id, boolean approve, UUID reviewerId, String comment) {
        ActualCost c = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
        assertCanReviewActualCost(c, approve, reviewerId, comment);
        financeScopeService.assertCanMutateActualCost(c);

        if (approve && c.getBudgetLineId() != null) {
            BudgetLine line = requireBudgetLine(c.getBudgetLineId());
            assertBudgetLineUsable(line);
            assertBudgetRemaining(line, c.getAmount());
            applyBudgetUsageOnce(line, c);
        }

        c.setStatus(approve ? ActualCostStatus.APPROVED : ActualCostStatus.REJECTED);
        c.setReviewedById(reviewerId);
        c.setReviewedAt(Instant.now());
        c.setReviewComment(comment == null ? null : comment.trim());

        ActualCost saved = repository.save(c);

        auditBuilderService.log(
                "actual_cost",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.ACTUAL_COST,
                "Фактическая стоимость обновлена",
                c,
                saved
        );
        return ActualCostDto.from(saved);
    }

    private void assertCanCreateActualCost(ActualCostDto request,
                                           WorkOrder workOrder,
                                           RepairRequest repairRequest,
                                           BudgetLine budgetLine) {
        if (request.amount() <= 0) {
            throw RestException.badRequest("Actual cost amount must be positive");
        }
        if (request.costCategoryId() == null) {
            throw RestException.badRequest("Cost category is required");
        }
        if (!hasTechnicalSource(request)) {
            throw RestException.badRequest(
                    "Actual cost must be linked to a technical source: workOrderId, repairRequestId, contractorWorkId, "
                            + "or sourceType/sourceId. budgetLineId is only an accounting dimension.");
        }
        if (request.sourceType() == ActualCostSourceType.WORK_ORDER_MANUAL_WITH_REASON
                && (request.notes() == null || request.notes().isBlank())) {
            throw RestException.badRequest("Manual work order actual cost requires a clear reason in notes");
        }
        if (request.contractorWorkId() != null
                && repository.existsByContractorWorkIdAndIsDeletedFalse(request.contractorWorkId())) {
            throw RestException.badRequest("Actual cost already exists for contractor work: " + request.contractorWorkId());
        }
        if (budgetLine != null) {
            assertBudgetLineUsable(budgetLine);
            assertBudgetLineCompatible(budgetLine, workOrder, repairRequest);
        }
    }

    private boolean hasTechnicalSource(ActualCostDto request) {
        return request.workOrderId() != null
                || request.repairRequestId() != null
                || request.contractorWorkId() != null
                || (request.sourceType() != null && request.sourceId() != null);
    }

    private ActualCostSourceType resolveSourceType(ActualCostDto request) {
        if (request.sourceType() != null) {
            return request.sourceType();
        }
        if (request.workOrderId() != null) {
            return ActualCostSourceType.WORK_ORDER;
        }
        if (request.repairRequestId() != null) {
            return ActualCostSourceType.REPAIR_REQUEST;
        }
        if (request.contractorWorkId() != null) {
            return ActualCostSourceType.CONTRACTOR_WORK;
        }
        return null;
    }

    private UUID resolveSourceId(ActualCostDto request) {
        if (request.sourceId() != null) {
            return request.sourceId();
        }
        if (request.workOrderId() != null) {
            return request.workOrderId();
        }
        if (request.repairRequestId() != null) {
            return request.repairRequestId();
        }
        if (request.contractorWorkId() != null) {
            return request.contractorWorkId();
        }
        return null;
    }

    private void assertCanReviewActualCost(ActualCost actualCost, boolean approve, UUID reviewerId, String comment) {
        if (reviewerId == null) {
            throw RestException.badRequest("reviewerId is required");
        }
        if (actualCost.getStatus() != ActualCostStatus.PENDING) {
            throw RestException.badRequest("Only PENDING actual costs can be reviewed");
        }
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest(approve ? "Approval comment is required" : "Rejection comment is required");
        }
    }

    private void assertBudgetLineUsable(BudgetLine line) {
        MaintenanceBudget budget = line.getBudget();
        if (budget == null) {
            throw RestException.badRequest("Budget line is not linked to a budget");
        }
        if (!USABLE_BUDGET_STATUSES.contains(budget.getStatus())) {
            throw RestException.badRequest("Budget must be APPROVED or LOCKED to register/approve actual costs");
        }
    }

    private void assertBudgetLineCompatible(BudgetLine line, WorkOrder workOrder, RepairRequest repairRequest) {
        MaintenanceBudget budget = line.getBudget();
        if (budget == null || budget.getDepartmentId() == null) {
            return;
        }
        UUID budgetDepartmentId = budget.getDepartmentId();
        if (workOrder != null && workOrder.getDepartmentId() != null
                && !budgetDepartmentId.equals(workOrder.getDepartmentId())) {
            throw RestException.badRequest("Budget line department does not match linked work order department");
        }
        if (repairRequest != null && repairRequest.getDepartmentId() != null
                && !budgetDepartmentId.equals(repairRequest.getDepartmentId())) {
            throw RestException.badRequest("Budget line department does not match linked repair request department");
        }
    }

    private void assertBudgetRemaining(BudgetLine line, double amount) {
        double alreadyApproved = repository.sumAmountByBudgetLineIdAndStatusAndIsDeletedFalse(
                line.getId(), ActualCostStatus.APPROVED);
        double remaining = line.getPlannedAmount() - alreadyApproved;
        if (amount - remaining > EPSILON) {
            throw RestException.badRequest(
                    "Actual cost amount exceeds budget line remaining amount (remaining=" + remaining + ")");
        }
    }

    private void applyBudgetUsageOnce(BudgetLine line, ActualCost cost) {
        if (cost.getStatus() == ActualCostStatus.APPROVED) {
            return;
        }
        line.setActualAmount(line.getActualAmount() + cost.getAmount());
        budgetLineRepository.save(line);

        MaintenanceBudget budget = line.getBudget();
        if (budget != null) {
            budget.setTotalActual(budget.getTotalActual() + cost.getAmount());
            maintenanceBudgetRepository.save(budget);
        }
    }

    private WorkOrder resolveEffectiveWorkOrder(WorkOrder workOrder, ContractorWork contractorWork) {
        if (workOrder != null) {
            return workOrder;
        }
        if (contractorWork == null || contractorWork.getWorkOrderId() == null) {
            return null;
        }
        return requireWorkOrder(contractorWork.getWorkOrderId());
    }

    private UUID resolveActualCostDepartment(WorkOrder workOrder, RepairRequest repairRequest, BudgetLine budgetLine) {
        if (workOrder != null && workOrder.getDepartmentId() != null) {
            return workOrder.getDepartmentId();
        }
        if (repairRequest != null && repairRequest.getDepartmentId() != null) {
            return repairRequest.getDepartmentId();
        }
        if (budgetLine != null && budgetLine.getBudget() != null) {
            return budgetLine.getBudget().getDepartmentId();
        }
        return null;
    }

    private WorkOrder requireWorkOrderIfPresent(UUID workOrderId) {
        return workOrderId == null ? null : requireWorkOrder(workOrderId);
    }

    private WorkOrder requireWorkOrder(UUID workOrderId) {
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
    }

    private RepairRequest requireRepairRequestIfPresent(UUID repairRequestId) {
        if (repairRequestId == null) {
            return null;
        }
        return repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + repairRequestId));
    }

    private ContractorWork requireContractorWorkIfPresent(UUID contractorWorkId) {
        if (contractorWorkId == null) {
            return null;
        }
        return contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId)
                .orElseThrow(() -> RestException.notFound("Contractor work not found: " + contractorWorkId));
    }

    private BudgetLine requireBudgetLineIfPresent(UUID budgetLineId) {
        return budgetLineId == null ? null : requireBudgetLine(budgetLineId);
    }

    private BudgetLine requireBudgetLine(UUID budgetLineId) {
        return budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)
                .orElseThrow(() -> RestException.notFound("Budget line not found: " + budgetLineId));
    }
}
