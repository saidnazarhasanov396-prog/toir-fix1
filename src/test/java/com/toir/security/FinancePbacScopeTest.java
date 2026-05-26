package com.toir.security;

import com.toir.controller.BudgetSummaryController;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
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
    UserRepository userRepository;
    EmployeeRepository employeeRepository;
    FinanceScopeService financeScopeService;
    BudgetSummaryController controller;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(MaintenanceBudgetRepository.class);
        lineRepository = mock(BudgetLineRepository.class);
        actualCostRepository = mock(ActualCostRepository.class);
        costCategoryRepository = mock(CostCategoryRepository.class);
        userRepository = mock(UserRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        financeScopeService = mock(FinanceScopeService.class);
        controller = new BudgetSummaryController(
                budgetRepository,
                lineRepository,
                actualCostRepository,
                costCategoryRepository,
                userRepository,
                employeeRepository,
                financeScopeService
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
        when(financeScopeService.filterBudgets(any())).thenReturn(List.of(allowedBudget));
        when(financeScopeService.filterBudgetLines(any())).thenReturn(List.of(allowedLine));

        var response = controller.summary();

        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().getFirst().id()).isEqualTo(allowedBudget.getId());
        assertThat(response.getBody().totalPlanned()).isEqualTo(allowedBudget.getTotalPlanned());
    }

    @Test
    void actualCostRegisterUsesScopedActualCosts() {
        ActualCost allowed = actualCost(UUID.randomUUID());
        ActualCost forbidden = actualCost(UUID.randomUUID());
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(allowed, forbidden));
        when(financeScopeService.filterActualCosts(any())).thenReturn(List.of(allowed));

        var response = controller.actualCostRegister(0, 20);

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
}
