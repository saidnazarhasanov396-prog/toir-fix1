package com.toir.security;

import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.FinanceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceScopeServiceTest {

    ScopeAccessService scopeAccessService;
    ActualCostRepository actualCostRepository;
    WorkOrderRepository workOrderRepository;
    RepairRequestRepository repairRequestRepository;
    BudgetLineRepository budgetLineRepository;
    ContractorWorkRepository contractorWorkRepository;
    FinanceScopeService service;

    @BeforeEach
    void setUp() {
        scopeAccessService = mock(ScopeAccessService.class);
        actualCostRepository = mock(ActualCostRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        repairRequestRepository = mock(RepairRequestRepository.class);
        budgetLineRepository = mock(BudgetLineRepository.class);
        contractorWorkRepository = mock(ContractorWorkRepository.class);
        service = new FinanceScopeService(
                scopeAccessService,
                actualCostRepository,
                workOrderRepository,
                repairRequestRepository,
                budgetLineRepository,
                contractorWorkRepository
        );
    }

    @Test
    void filterBudgetsKeepsOnlyAccessibleDepartmentForNonAdmin() {
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID forbiddenDepartmentId = UUID.randomUUID();
        MaintenanceBudget allowed = budget(UUID.randomUUID(), allowedDepartmentId);
        MaintenanceBudget forbidden = budget(UUID.randomUUID(), forbiddenDepartmentId);
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(forbiddenDepartmentId)).thenReturn(false);

        var result = service.filterBudgets(List.of(allowed, forbidden));

        assertThat(result).containsExactly(allowed);
    }

    @Test
    void scopeAdminBudgetFilterKeepsAllBudgets() {
        MaintenanceBudget first = budget(UUID.randomUUID(), UUID.randomUUID());
        MaintenanceBudget second = budget(UUID.randomUUID(), UUID.randomUUID());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);

        var result = service.filterBudgets(List.of(first, second));

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void filterBudgetLinesKeepsOnlyLinesFromAccessibleBudgets() {
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID forbiddenDepartmentId = UUID.randomUUID();
        BudgetLine allowed = budgetLine(UUID.randomUUID(), allowedDepartmentId);
        BudgetLine forbidden = budgetLine(UUID.randomUUID(), forbiddenDepartmentId);
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(forbiddenDepartmentId)).thenReturn(false);

        var result = service.filterBudgetLines(List.of(allowed, forbidden));

        assertThat(result).containsExactly(allowed);
    }

    @Test
    void filterActualCostsKeepsAllSupportedSourcesInAccessibleDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID contractorWorkOrderId = UUID.randomUUID();
        ActualCost workOrderCost = actualCost(UUID.randomUUID());
        workOrderCost.setWorkOrderId(workOrderId);
        ActualCost repairCost = actualCost(UUID.randomUUID());
        repairCost.setRepairRequestId(repairRequestId);
        ActualCost budgetLineCost = actualCost(UUID.randomUUID());
        budgetLineCost.setBudgetLineId(budgetLineId);
        ActualCost contractorCost = actualCost(UUID.randomUUID());
        contractorCost.setContractorWorkId(contractorWorkId);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, departmentId)));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId))
                .thenReturn(Optional.of(budgetLine(budgetLineId, departmentId)));
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId))
                .thenReturn(Optional.of(contractorWork(contractorWorkId, contractorWorkOrderId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(contractorWorkOrderId))
                .thenReturn(Optional.of(workOrder(contractorWorkOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = service.filterActualCosts(List.of(workOrderCost, repairCost, budgetLineCost, contractorCost));

        assertThat(result).containsExactly(workOrderCost, repairCost, budgetLineCost, contractorCost);
    }

    @Test
    void filterActualCostsDropsContractorWorkFromForbiddenDepartment() {
        UUID forbiddenDepartmentId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        ActualCost contractorCost = actualCost(UUID.randomUUID());
        contractorCost.setContractorWorkId(contractorWorkId);
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId))
                .thenReturn(Optional.of(contractorWork(contractorWorkId, workOrderId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, forbiddenDepartmentId)));
        when(scopeAccessService.canAccessDepartment(forbiddenDepartmentId)).thenReturn(false);

        var result = service.filterActualCosts(List.of(contractorCost));

        assertThat(result).isEmpty();
    }

    @Test
    void filterActualCostsUsesBudgetLineDepartmentWhenWorkOrderDiffers() {
        UUID budgetDepartmentId = UUID.randomUUID();
        UUID workOrderDepartmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost cost = actualCost(UUID.randomUUID());
        cost.setBudgetLineId(budgetLineId);
        cost.setWorkOrderId(workOrderId);

        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId))
                .thenReturn(Optional.of(budgetLine(budgetLineId, budgetDepartmentId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, workOrderDepartmentId)));
        when(scopeAccessService.canAccessDepartment(budgetDepartmentId)).thenReturn(true);

        var result = service.filterActualCosts(List.of(cost));

        assertThat(result).containsExactly(cost);
    }

    @Test
    void filterActualCostsDeniesWhenPrimaryBudgetLineDepartmentForbiddenEvenIfWorkOrderAccessible() {
        UUID budgetDepartmentId = UUID.randomUUID();
        UUID workOrderDepartmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        ActualCost cost = actualCost(UUID.randomUUID());
        cost.setBudgetLineId(budgetLineId);
        cost.setWorkOrderId(workOrderId);

        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId))
                .thenReturn(Optional.of(budgetLine(budgetLineId, budgetDepartmentId)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, workOrderDepartmentId)));
        when(scopeAccessService.canAccessDepartment(budgetDepartmentId)).thenReturn(false);

        var result = service.filterActualCosts(List.of(cost));

        assertThat(result).isEmpty();
    }

    private MaintenanceBudget budget(UUID id, UUID departmentId) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(id);
        budget.setDepartmentId(departmentId);
        return budget;
    }

    private BudgetLine budgetLine(UUID id, UUID departmentId) {
        BudgetLine line = new BudgetLine();
        line.setId(id);
        line.setBudget(budget(UUID.randomUUID(), departmentId));
        return line;
    }

    private ActualCost actualCost(UUID id) {
        ActualCost cost = new ActualCost();
        cost.setId(id);
        cost.setAmount(java.math.BigDecimal.valueOf(100));
        return cost;
    }

    private WorkOrder workOrder(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setDepartmentId(departmentId);
        return workOrder;
    }

    private RepairRequest repairRequest(UUID id, UUID departmentId) {
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(id);
        repairRequest.setDepartmentId(departmentId);
        return repairRequest;
    }

    private ContractorWork contractorWork(UUID id, UUID workOrderId) {
        ContractorWork contractorWork = new ContractorWork();
        contractorWork.setId(id);
        contractorWork.setWorkOrderId(workOrderId);
        return contractorWork;
    }
}
