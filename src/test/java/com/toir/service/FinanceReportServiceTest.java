package com.toir.service;

import com.toir.dto.budget.FinanceDashboardResponse;
import com.toir.entity.Department;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.BudgetStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceReportServiceTest {

    private MaintenanceBudgetRepository budgetRepository;
    private BudgetLineRepository lineRepository;
    private ActualCostRepository actualCostRepository;
    private DepartmentRepository departmentRepository;
    private CostCategoryRepository costCategoryRepository;
    private FinanceScopeService financeScopeService;
    private FinanceReportService service;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(MaintenanceBudgetRepository.class);
        lineRepository = mock(BudgetLineRepository.class);
        actualCostRepository = mock(ActualCostRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        costCategoryRepository = mock(CostCategoryRepository.class);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        RepairRequestRepository repairRequestRepository = mock(RepairRequestRepository.class);
        ContractorWorkRepository contractorWorkRepository = mock(ContractorWorkRepository.class);
        financeScopeService = mock(FinanceScopeService.class);
        service = new FinanceReportService(
                budgetRepository,
                lineRepository,
                actualCostRepository,
                departmentRepository,
                costCategoryRepository,
                workOrderRepository,
                repairRequestRepository,
                contractorWorkRepository,
                financeScopeService
        );
        when(financeScopeService.filterBudgets(any())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
        when(financeScopeService.filterBudgetLines(any())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
        when(financeScopeService.filterActualCosts(any())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
    }

    @Test
    void dashboardSeparatesApprovedPendingRejectedAndUnallocatedTotals() {
        UUID departmentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, departmentId);
        BudgetLine line = line(lineId, budget, categoryId, 1_000);
        budget.getLines().add(line);
        Department department = department(departmentId);
        CostCategory category = category(categoryId);

        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(budget));
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(line));
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                cost(lineId, categoryId, ActualCostStatus.APPROVED, 200),
                cost(lineId, categoryId, ActualCostStatus.PENDING, 150),
                cost(lineId, categoryId, ActualCostStatus.REJECTED, 30),
                cost(null, categoryId, ActualCostStatus.PENDING, 50)
        ));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(department));
        when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(category));

        FinanceDashboardResponse dashboard = service.dashboard(2026, 6, departmentId);

        assertThat(dashboard.totalPlanned()).isEqualTo(1_000);
        assertThat(dashboard.approvedActual()).isEqualTo(200);
        assertThat(dashboard.pendingActual()).isEqualTo(200);
        assertThat(dashboard.rejectedActual()).isEqualTo(30);
        assertThat(dashboard.remainingBudget()).isEqualTo(800);
        assertThat(dashboard.forecastRemaining()).isEqualTo(600);
        assertThat(dashboard.unallocatedAmount()).isEqualTo(50);
        assertThat(dashboard.byDepartment()).hasSize(1);
        assertThat(dashboard.byDepartment().getFirst().plannedAmount()).isEqualTo(1_000);
        assertThat(dashboard.byCategory()).hasSize(1);
        assertThat(dashboard.byCategory().getFirst().pendingActualAmount()).isEqualTo(200);
    }

    @Test
    void departmentReportCsvIncludesFiltersAndReconciliationIds() {
        UUID departmentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, departmentId);
        BudgetLine line = line(lineId, budget, categoryId, 500);
        budget.getLines().add(line);
        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(budget));
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(line));
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(cost(lineId, categoryId, ActualCostStatus.APPROVED, 125)));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(department(departmentId)));
        when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(category(categoryId)));

        String csv = service.planVsActualByDepartmentCsv(2026, 6, departmentId).content();

        assertThat(csv).contains("generatedAt", "filterYear", "filterMonth", "groupId", departmentId.toString());
        assertThat(csv).contains("plannedAmount", "approvedActualAmount", "pendingActualAmount");
    }

    private MaintenanceBudget budget(UUID id, UUID departmentId) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(id);
        budget.setYear(2026);
        budget.setMonth(6);
        budget.setDepartmentId(departmentId);
        budget.setStatus(BudgetStatus.APPROVED);
        budget.setTotalPlanned(1_000);
        return budget;
    }

    private BudgetLine line(UUID id, MaintenanceBudget budget, UUID categoryId, double plannedAmount) {
        BudgetLine line = new BudgetLine();
        line.setId(id);
        line.setBudget(budget);
        line.setCostCategoryId(categoryId);
        line.setPlannedAmount(plannedAmount);
        return line;
    }

    private ActualCost cost(UUID budgetLineId, UUID categoryId, ActualCostStatus status, double amount) {
        ActualCost cost = new ActualCost();
        cost.setId(UUID.randomUUID());
        cost.setBudgetLineId(budgetLineId);
        cost.setCostCategoryId(categoryId);
        cost.setStatus(status);
        cost.setAmount(amount);
        cost.setCostDate(Instant.parse("2026-06-15T00:00:00Z"));
        return cost;
    }

    private Department department(UUID id) {
        Department department = new Department();
        department.setId(id);
        department.setCode("D-1");
        department.setName("Maintenance");
        return department;
    }

    private CostCategory category(UUID id) {
        CostCategory category = new CostCategory();
        category.setId(id);
        category.setCode("MAT");
        category.setName("Materials");
        return category;
    }
}
