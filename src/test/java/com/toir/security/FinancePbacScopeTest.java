package com.toir.security;

import com.toir.controller.BudgetSummaryController;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.financialreview.ActualCostReviewItem;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.ActualCostReviewFacadeService;
import com.toir.service.CounteragentService;
import com.toir.service.FinanceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancePbacScopeTest {

    MaintenanceBudgetRepository budgetRepository;
    BudgetLineRepository lineRepository;
    ActualCostRepository actualCostRepository;
    CostCategoryRepository costCategoryRepository;
    DepartmentRepository departmentRepository;
    UserRepository userRepository;
    EmployeeRepository employeeRepository;
    ContractorWorkRepository contractorWorkRepository;
    CounteragentService counteragentService;
    WorkOrderRepository workOrderRepository;
    FinanceScopeService financeScopeService;
    ActualCostReviewFacadeService actualCostReviewFacadeService;
    BudgetSummaryController controller;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(MaintenanceBudgetRepository.class);
        lineRepository = mock(BudgetLineRepository.class);
        actualCostRepository = mock(ActualCostRepository.class);
        costCategoryRepository = mock(CostCategoryRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        userRepository = mock(UserRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        contractorWorkRepository = mock(ContractorWorkRepository.class);
        counteragentService = mock(CounteragentService.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        financeScopeService = mock(FinanceScopeService.class);
        actualCostReviewFacadeService = mock(ActualCostReviewFacadeService.class);
        controller = new BudgetSummaryController(
                budgetRepository,
                lineRepository,
                actualCostRepository,
                costCategoryRepository,
                departmentRepository,
                userRepository,
                employeeRepository,
                contractorWorkRepository,
                counteragentService,
                workOrderRepository,
                financeScopeService,
                actualCostReviewFacadeService
        );
    }

    @Test
    void budgetSummaryUsesScopedBudgetsAndLines() {
        MaintenanceBudget allowedBudget = budget(UUID.randomUUID());
        MaintenanceBudget forbiddenBudget = budget(UUID.randomUUID());
        BudgetLine allowedLine = line(UUID.randomUUID(), allowedBudget);
        BudgetLine forbiddenLine = line(UUID.randomUUID(), forbiddenBudget);
        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(allowedBudget, forbiddenBudget));
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(allowedLine, forbiddenLine));
        when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(financeScopeService.filterBudgets(any())).thenReturn(List.of(allowedBudget));
        when(financeScopeService.filterBudgetLines(any())).thenReturn(List.of(allowedLine));

        var response = controller.summary();

        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().getFirst().id()).isEqualTo(allowedBudget.getId());
        assertThat(response.getBody().totalPlanned()).isEqualTo(allowedBudget.getTotalPlanned());
    }

    @Test
    void budgetSummaryReportsApprovedPendingAvailableAndUnallocatedPipeline() {
        MaintenanceBudget budget = budget(UUID.randomUUID());
        budget.setTotalPlanned(1000);
        budget.setTotalActual(0);
        BudgetLine line = line(UUID.randomUUID(), budget);
        line.setPlannedAmount(1000);
        line.setActualAmount(0);
        ActualCost approved = actualCost(UUID.randomUUID());
        approved.setBudgetLineId(line.getId());
        approved.setStatus(ActualCostStatus.APPROVED);
        approved.setAmount(600);
        ActualCost pending = actualCost(UUID.randomUUID());
        pending.setBudgetLineId(line.getId());
        pending.setStatus(ActualCostStatus.PENDING);
        pending.setAmount(350);
        ActualCost unallocated = actualCost(UUID.randomUUID());
        unallocated.setBudgetLineId(null);
        unallocated.setStatus(ActualCostStatus.PENDING);
        unallocated.setAmount(25);

        when(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(budget));
        when(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(line));
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(approved, pending, unallocated));
        when(costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(financeScopeService.filterBudgets(any())).thenReturn(List.of(budget));
        when(financeScopeService.filterBudgetLines(any())).thenReturn(List.of(line));
        when(financeScopeService.filterActualCosts(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.summary(2026, 5, budget.getDepartmentId()).getBody();

        assertThat(response.totalActual()).isEqualTo(600);
        assertThat(response.totalCommitted()).isEqualTo(350);
        assertThat(response.pendingReviewAmount()).isEqualTo(350);
        assertThat(response.totalAvailable()).isEqualTo(50);
        assertThat(response.unallocatedActualAmount()).isEqualTo(25);
        assertThat(response.atRiskBudgetLineCount()).isEqualTo(1);
        assertThat(response.overBudgetLineCount()).isZero();
        assertThat(response.byCategory().getFirst().actualAmount()).isEqualTo(600);
        assertThat(response.byCategory().getFirst().committedAmount()).isEqualTo(350);
        assertThat(response.byCategory().getFirst().availableAmount()).isEqualTo(50);
    }

    @Test
    void actualCostRegisterUsesScopedActualCosts() {
        ActualCost allowed = actualCost(UUID.randomUUID());
        ActualCostReviewItem item = reviewItem(allowed);
        when(actualCostReviewFacadeService.actualCostRegister(null)).thenReturn(List.of(item));
        when(actualCostReviewFacadeService.registerSummary(List.of(item))).thenReturn(registerSummary(List.of(item)));

        var response = controller.actualCostRegister(0, 20, null, null, null, null, null, null, null);

        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().getFirst().id()).isEqualTo(allowed.getId());
        assertThat(response.getBody().summary().totalCount()).isEqualTo(1);
    }

    @Test
    void reviewHistoryForbiddenActualCostReturns403() {
        UUID actualCostId = UUID.randomUUID();
        ActualCost actualCost = actualCost(actualCostId);
        when(actualCostRepository.findByIdAndIsDeletedFalse(actualCostId)).thenReturn(Optional.of(actualCost));
        doThrow(new AccessDeniedException("denied"))
                .when(financeScopeService).assertCanReadActualCost(actualCost);

        assertThatThrownBy(() -> controller.reviewHistory(actualCostId))
                .isInstanceOf(AccessDeniedException.class);
    }

    private MaintenanceBudget budget(UUID id) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(id);
        budget.setYear(2026);
        budget.setMonth(5);
        budget.setDepartmentId(UUID.randomUUID());
        budget.setTotalPlanned(100);
        budget.setTotalActual(40);
        return budget;
    }

    private BudgetLine line(UUID id, MaintenanceBudget budget) {
        BudgetLine line = new BudgetLine();
        line.setId(id);
        line.setBudget(budget);
        line.setCostCategoryId(UUID.randomUUID());
        line.setPlannedAmount(100);
        line.setActualAmount(40);
        return line;
    }

    private ActualCost actualCost(UUID id) {
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(100);
        actualCost.setCostDate(Instant.parse("2026-05-01T00:00:00Z"));
        return actualCost;
    }

    private ActualCostReviewItem reviewItem(ActualCost actualCost) {
        return new ActualCostReviewItem(
                actualCost.getId(),
                actualCost.getWorkOrderId(),
                actualCost.getRepairRequestId(),
                actualCost.getContractorWorkId(),
                actualCost.getCostCategoryId(),
                actualCost.getStatus().name(),
                actualCost.getAmount(),
                actualCost.getCostDate(),
                actualCost.getNotes(),
                actualCost.getReviewedAt(),
                actualCost.getReviewedById(),
                null,
                null,
                actualCost.getReviewComment(),
                null,
                null,
                null,
                null,
                null,
                actualCost.getCostCategoryId() != null
                        ? new ActualCostReviewItem.Ref(actualCost.getCostCategoryId(), "", "")
                        : null,
                0,
                false,
                "/financial-review/history/" + actualCost.getId(),
                null,
                "FINANCE_MANAGER",
                null,
                24,
                "RULE",
                null,
                actualCost.getStatus() == ActualCostStatus.PENDING,
                null,
                "FINANCE_MANAGER",
                "GENERAL",
                "/budgets?actualCostId=" + actualCost.getId(),
                "/financial-review?actualCostId=" + actualCost.getId(),
                "/financial-review?actualCostId=" + actualCost.getId()
        );
    }

    private ActualCostRegisterSummary registerSummary(List<ActualCostReviewItem> items) {
        return new ActualCostRegisterSummary(
                items.stream().mapToDouble(ActualCostReviewItem::amount).sum(),
                items.stream().filter(item -> "APPROVED".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum(),
                items.stream().filter(item -> "PENDING".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum(),
                items.stream().filter(item -> "REJECTED".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum(),
                items.size(),
                items.stream().filter(item -> "APPROVED".equals(item.status())).count(),
                items.stream().filter(item -> "PENDING".equals(item.status())).count(),
                items.stream().filter(item -> "REJECTED".equals(item.status())).count()
        );
    }
}
