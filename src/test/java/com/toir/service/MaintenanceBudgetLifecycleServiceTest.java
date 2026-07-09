package com.toir.service;

import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.WebhookService;
import com.toir.service.maintanance.MaintenanceBudgetService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceBudgetLifecycleServiceTest {

    private MaintenanceBudgetRepository repository;
    private BudgetLineRepository lineRepository;
    private ScopeAccessService scopeAccessService;
    private BudgetEventRepository budgetEventRepository;
    private MaintenanceBudgetService service;

    @BeforeEach
    void setUp() {
        repository = mock(MaintenanceBudgetRepository.class);
        lineRepository = mock(BudgetLineRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        CostCategoryRepository costCategoryRepository = mock(CostCategoryRepository.class);
        AuditBuilderService auditBuilderService = mock(AuditBuilderService.class);
        scopeAccessService = mock(ScopeAccessService.class);
        budgetEventRepository = mock(BudgetEventRepository.class);
        WebhookService webhookService = mock(WebhookService.class);
        service = new MaintenanceBudgetService(
                repository,
                lineRepository,
                departmentRepository,
                costCategoryRepository,
                auditBuilderService,
                scopeAccessService,
                budgetEventRepository,
                webhookService
        );
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.save(any(MaintenanceBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepository.save(any(BudgetLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lifecycleCommandsMoveBudgetThroughControlledStatesAndAuditEachStep() {
        UUID budgetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));

        assertThat(service.submit(budgetId, actorId, "ready").status()).isEqualTo(BudgetStatus.SUBMITTED);
        assertThat(service.approve(budgetId, actorId, "approved").status()).isEqualTo(BudgetStatus.APPROVED);
        assertThat(service.lock(budgetId, actorId, "period locked").status()).isEqualTo(BudgetStatus.LOCKED);
        assertThat(service.close(budgetId, actorId, "period closed").status()).isEqualTo(BudgetStatus.CLOSED);
        assertThat(service.reopen(budgetId, actorId, "correction window").status()).isEqualTo(BudgetStatus.LOCKED);

        verify(budgetEventRepository, atLeast(5)).save(any(BudgetEvent.class));
    }

    @Test
    void approveRequiresSubmittedBudget() {
        UUID budgetId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));

        assertThatThrownBy(() -> service.approve(budgetId, UUID.randomUUID(), "approved"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only SUBMITTED budgets can be approved");
    }

    @Test
    void transferMovesPlanBetweenLinesAndKeepsBudgetTotal() {
        UUID budgetId = UUID.randomUUID();
        UUID fromLineId = UUID.randomUUID();
        UUID toLineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.APPROVED);
        BudgetLine from = line(fromLineId, budget, 700, 200);
        BudgetLine to = line(toLineId, budget, 300, 100);
        budget.getLines().add(from);
        budget.getLines().add(to);
        budget.setTotalPlanned(1_000);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));
        when(lineRepository.findByIdAndIsDeletedFalse(fromLineId)).thenReturn(Optional.of(from));
        when(lineRepository.findByIdAndIsDeletedFalse(toLineId)).thenReturn(Optional.of(to));

        MaintenanceBudgetDto result = service.transfer(
                budgetId,
                fromLineId,
                toLineId,
                150,
                UUID.randomUUID(),
                "shift reserve");

        assertThat(from.getPlannedAmount()).isEqualTo(550);
        assertThat(to.getPlannedAmount()).isEqualTo(450);
        assertThat(result.totalPlanned()).isEqualTo(1_000);
        verify(budgetEventRepository).save(any(BudgetEvent.class));
    }

    @Test
    void transferCannotReduceLineBelowApprovedActualAmount() {
        UUID budgetId = UUID.randomUUID();
        UUID fromLineId = UUID.randomUUID();
        UUID toLineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.APPROVED);
        BudgetLine from = line(fromLineId, budget, 700, 650);
        BudgetLine to = line(toLineId, budget, 300, 0);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));
        when(lineRepository.findByIdAndIsDeletedFalse(fromLineId)).thenReturn(Optional.of(from));
        when(lineRepository.findByIdAndIsDeletedFalse(toLineId)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.transfer(budgetId, fromLineId, toLineId, 100, UUID.randomUUID(), "shift"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("below approved actual and committed amounts");
    }

    @Test
    void transferRejectsWhenTargetLineWouldDropBelowCommittedAmount() {
        UUID budgetId = UUID.randomUUID();
        UUID fromLineId = UUID.randomUUID();
        UUID toLineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.APPROVED);
        BudgetLine from = line(fromLineId, budget, 500, 100);
        from.setCommittedAmount(450);
        BudgetLine to = line(toLineId, budget, 200, 0);
        budget.getLines().add(from);
        budget.getLines().add(to);
        budget.setTotalPlanned(700);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));
        when(lineRepository.findByIdAndIsDeletedFalse(fromLineId)).thenReturn(Optional.of(from));
        when(lineRepository.findByIdAndIsDeletedFalse(toLineId)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.transfer(budgetId, fromLineId, toLineId, 100, UUID.randomUUID(), "shift"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("below approved actual and committed amounts");
    }

    @Test
    void reviseChangesBudgetTotalAndCannotDropLineBelowActualAmount() {
        UUID budgetId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.APPROVED);
        BudgetLine line = line(lineId, budget, 500, 200);
        budget.getLines().add(line);
        budget.setTotalPlanned(500);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));
        when(lineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));

        MaintenanceBudgetDto result = service.reviseLine(budgetId, lineId, 650, UUID.randomUUID(), "increase plan");

        assertThat(line.getPlannedAmount()).isEqualTo(650);
        assertThat(result.totalPlanned()).isEqualTo(650);

        assertThatThrownBy(() -> service.reviseLine(budgetId, lineId, 100, UUID.randomUUID(), "too low"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("below approved actual and committed amounts");
    }

    @Test
    void reviseAllowedOnLockedBudgetForResponsibleFinanceUser() {
        UUID budgetId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, BudgetStatus.LOCKED);
        BudgetLine line = line(lineId, budget, 500, 0);
        budget.getLines().add(line);
        budget.setTotalPlanned(500);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));
        when(lineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));

        MaintenanceBudgetDto result = service.reviseLine(budgetId, lineId, 800, UUID.randomUUID(), "unplanned absorb");

        assertThat(line.getPlannedAmount()).isEqualTo(800);
        assertThat(result.totalPlanned()).isEqualTo(800);
    }

    private MaintenanceBudget budget(UUID id, BudgetStatus status) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(id);
        budget.setYear(2026);
        budget.setMonth(6);
        budget.setDepartmentId(UUID.randomUUID());
        budget.setStatus(status);
        return budget;
    }

    private BudgetLine line(UUID id, MaintenanceBudget budget, double planned, double actual) {
        BudgetLine line = new BudgetLine();
        line.setId(id);
        line.setBudget(budget);
        line.setCostCategoryId(UUID.randomUUID());
        line.setPlannedAmount(planned);
        line.setActualAmount(actual);
        return line;
    }
}
