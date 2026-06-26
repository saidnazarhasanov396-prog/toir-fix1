package com.toir.service;

import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceScopeService {

    private final ScopeAccessService scopeAccessService;
    private final ActualCostRepository actualCostRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final ContractorWorkRepository contractorWorkRepository;

    public List<MaintenanceBudget> filterBudgets(Collection<MaintenanceBudget> budgets) {
        if (budgets == null) {
            return List.of();
        }
        if (scopeAccessService.isScopeAdmin()) {
            return List.copyOf(budgets);
        }
        return budgets.stream()
                .filter(this::canAccessBudget)
                .toList();
    }

    public List<BudgetLine> filterBudgetLines(Collection<BudgetLine> lines) {
        if (lines == null) {
            return List.of();
        }
        if (scopeAccessService.isScopeAdmin()) {
            return List.copyOf(lines);
        }
        return lines.stream()
                .filter(this::canAccessBudgetLine)
                .toList();
    }

    public List<ActualCost> filterActualCosts(Collection<ActualCost> actualCosts) {
        if (actualCosts == null) {
            return List.of();
        }
        if (scopeAccessService.isScopeAdmin()) {
            return List.copyOf(actualCosts);
        }
        return actualCosts.stream()
                .filter(this::canReadActualCost)
                .toList();
    }

    public List<ActualCostReviewRouteOverride> filterRouteOverrides(
            Collection<ActualCostReviewRouteOverride> overrides
    ) {
        if (overrides == null) {
            return List.of();
        }
        if (scopeAccessService.isScopeAdmin()) {
            return List.copyOf(overrides);
        }
        return overrides.stream()
                .filter(this::canAccessRouteOverride)
                .toList();
    }

    public void assertCanAccessBudget(MaintenanceBudget budget) {
        if (!canAccessBudget(budget)) {
            throw forbidden();
        }
    }

    public void assertCanCreateBudget(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw forbidden();
        }
    }

    public void assertCanReadActualCost(ActualCost actualCost) {
        if (!canReadActualCost(actualCost)) {
            throw forbidden();
        }
    }

    public void assertCanMutateActualCost(ActualCost actualCost) {
        if (!canAccessActualCostDepartment(actualCost)) {
            throw forbidden();
        }
    }

    public void assertCanAccessRouteOverride(ActualCostReviewRouteOverride override) {
        if (!canAccessRouteOverride(override)) {
            throw forbidden();
        }
    }

    public void assertCanApplyRouteOverride(ActualCostReviewRouteOverride override, ActualCost actualCost) {
        if (!canAccessRouteOverride(override, actualCost)) {
            throw forbidden();
        }
    }

    private boolean canAccessBudget(MaintenanceBudget budget) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return budget != null
                && budget.getDepartmentId() != null
                && scopeAccessService.canAccessDepartment(budget.getDepartmentId());
    }

    private boolean canAccessBudgetLine(BudgetLine line) {
        return line != null && canAccessBudget(line.getBudget());
    }

    private boolean canReadActualCost(ActualCost actualCost) {
        return canAccessActualCostDepartment(actualCost) || canReadReviewer(actualCost);
    }

    private boolean canAccessActualCostDepartment(ActualCost actualCost) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        Set<UUID> departments = resolveActualCostDepartmentIds(actualCost);
        if (departments.size() != 1) {
            return false;
        }
        return scopeAccessService.canAccessDepartment(departments.iterator().next());
    }

    private boolean canReadReviewer(ActualCost actualCost) {
        if (actualCost == null || actualCost.getReviewedById() == null) {
            return false;
        }
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        if (actualCost.getReviewedById().equals(currentUserId)) {
            return true;
        }
        return scopeAccessService.currentEmployeeId()
                .map(actualCost.getReviewedById()::equals)
                .orElse(false);
    }

    private boolean canAccessRouteOverride(ActualCostReviewRouteOverride override) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        ActualCost actualCost = null;
        if (override != null && override.getActualCostId() != null) {
            actualCost = actualCostRepository.findByIdAndIsDeletedFalse(override.getActualCostId()).orElse(null);
        }
        return canAccessRouteOverride(override, actualCost);
    }

    private boolean canAccessRouteOverride(ActualCostReviewRouteOverride override, ActualCost actualCost) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        if (override == null) {
            return false;
        }
        Set<UUID> departments = new LinkedHashSet<>();
        if (override.getDepartmentId() != null) {
            departments.add(override.getDepartmentId());
        }
        departments.addAll(resolveActualCostDepartmentIds(actualCost));
        if (departments.size() != 1) {
            return false;
        }
        return scopeAccessService.canAccessDepartment(departments.iterator().next());
    }

    private Set<UUID> resolveActualCostDepartmentIds(ActualCost actualCost) {
        Set<UUID> departments = new LinkedHashSet<>();
        if (actualCost == null) {
            return departments;
        }
        if (actualCost.getWorkOrderId() != null) {
            workOrderRepository.findByIdAndIsDeletedFalse(actualCost.getWorkOrderId())
                    .map(WorkOrder::getDepartmentId)
                    .ifPresent(departments::add);
        }
        if (actualCost.getRepairRequestId() != null) {
            repairRequestRepository.findByIdAndIsDeletedFalse(actualCost.getRepairRequestId())
                    .map(RepairRequest::getDepartmentId)
                    .ifPresent(departments::add);
        }
        if (actualCost.getBudgetLineId() != null) {
            budgetLineRepository.findByIdAndIsDeletedFalse(actualCost.getBudgetLineId())
                    .map(BudgetLine::getBudget)
                    .map(MaintenanceBudget::getDepartmentId)
                    .ifPresent(departments::add);
        }
        if (actualCost.getContractorWorkId() != null) {
            contractorWorkRepository.findByIdAndIsDeletedFalse(actualCost.getContractorWorkId())
                    .map(ContractorWork::getWorkOrderId)
                    .flatMap(workOrderRepository::findByIdAndIsDeletedFalse)
                    .map(WorkOrder::getDepartmentId)
                    .ifPresent(departments::add);
        }
        return departments;
    }

    private AccessDeniedException forbidden() {
        return new AccessDeniedException("Access denied by finance scope");
    }
}
