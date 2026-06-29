package com.toir.security;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.service.maintanance.MaintenanceBudgetService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetPbacScopeTest {

    MaintenanceBudgetRepository repository;
    BudgetLineRepository lineRepository;
    DepartmentRepository departmentRepository;
    AuditBuilderService auditBuilderService;
    ScopeAccessService scopeAccessService;
    MaintenanceBudgetService service;

    @BeforeEach
    void setUp() {
        repository = mock(MaintenanceBudgetRepository.class);
        lineRepository = mock(BudgetLineRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        scopeAccessService = mock(ScopeAccessService.class);
        service = new MaintenanceBudgetService(
                repository,
                lineRepository,
                departmentRepository,
                auditBuilderService,
                scopeAccessService,
                mock(BudgetEventRepository.class));
    }

    @Test
    void scopeAdminCanListAllBudgetsForYear() {
        MaintenanceBudget first = budget(UUID.randomUUID(), UUID.randomUUID(), BudgetStatus.DRAFT);
        MaintenanceBudget second = budget(UUID.randomUUID(), UUID.randomUUID(), BudgetStatus.APPROVED);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByYearAndIsDeletedFalse(2026)).thenReturn(List.of(first, second));

        var result = service.findByYear(2026);

        assertThat(result).extracting(MaintenanceBudgetDto::id).containsExactly(first.getId(), second.getId());
    }

    @Test
    void nonAdminListUsesOwnDepartmentOnly() {
        UUID currentDepartmentId = UUID.randomUUID();
        MaintenanceBudget allowed = budget(UUID.randomUUID(), currentDepartmentId, BudgetStatus.DRAFT);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(currentDepartmentId);
        when(repository.findAllByDepartmentIdAndYearAndIsDeletedFalse(currentDepartmentId, 2026))
                .thenReturn(List.of(allowed));

        var result = service.findByYear(2026);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departmentId()).isEqualTo(currentDepartmentId);
        verify(repository).findAllByDepartmentIdAndYearAndIsDeletedFalse(currentDepartmentId, 2026);
    }

    @Test
    void scopeAdminCanFilterBudgetsByYearMonthDepartmentAndSort() {
        UUID targetDepartmentId = UUID.randomUUID();
        MaintenanceBudget oldBudget = budget(UUID.randomUUID(), targetDepartmentId, BudgetStatus.DRAFT);
        oldBudget.setYear(2025);
        oldBudget.setTotalPlanned(900);
        MaintenanceBudget wrongMonth = budget(UUID.randomUUID(), targetDepartmentId, BudgetStatus.APPROVED);
        wrongMonth.setMonth(4);
        wrongMonth.setTotalPlanned(800);
        MaintenanceBudget smaller = budget(UUID.randomUUID(), targetDepartmentId, BudgetStatus.APPROVED);
        smaller.setTotalPlanned(100);
        MaintenanceBudget larger = budget(UUID.randomUUID(), targetDepartmentId, BudgetStatus.APPROVED);
        larger.setTotalPlanned(300);
        MaintenanceBudget wrongDepartment = budget(UUID.randomUUID(), UUID.randomUUID(), BudgetStatus.APPROVED);
        wrongDepartment.setTotalPlanned(500);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(oldBudget, wrongMonth, smaller, larger, wrongDepartment));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());

        var result = service.findFiltered(2026, 5, targetDepartmentId, "totalPlanned", "desc");

        assertThat(result).extracting(MaintenanceBudgetDto::id)
                .containsExactly(larger.getId(), smaller.getId());
    }

    @Test
    void nonAdminWithoutDepartmentCannotReceiveGlobalBudgets() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);

        assertThatThrownBy(() -> service.findByYear(2026))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findAllByYearAndIsDeletedFalse(2026);
    }

    @Test
    void detailForbiddenBudgetReturns403() {
        UUID budgetId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(budgetId))
                .thenReturn(Optional.of(budget(budgetId, departmentId, BudgetStatus.DRAFT)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(budgetId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingBudgetRemains404() {
        UUID budgetId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(budgetId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Budget not found");
    }

    @Test
    void createWithForbiddenDepartmentReturns403() {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new MaintenanceBudgetDto(
                null,
                2026,
                5,
                departmentId,
                null,
                BudgetStatus.DRAFT,
                0,
                0,
                List.of()
        ))).isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(MaintenanceBudget.class));
    }

    @Test
    void approveForbiddenBudgetReturns403BeforeStatusRules() {
        UUID budgetId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceBudget existing = budget(budgetId, departmentId, BudgetStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(existing));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(budgetId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(existing);
    }

    @Test
    void addLineAllowedBudgetSucceeds() {
        UUID budgetId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceBudget existing = budget(budgetId, departmentId, BudgetStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(existing));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(lineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(existing)).thenReturn(existing);

        service.addLine(budgetId, new BudgetLineDto(null, UUID.randomUUID(), "Pump", 100, 0));

        assertThat(existing.getTotalPlanned()).isEqualTo(100);
    }

    @Test
    void addLineToApprovedBudgetIsBlocked() {
        UUID budgetId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceBudget existing = budget(budgetId, departmentId, BudgetStatus.APPROVED);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(existing));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.addLine(
                budgetId,
                new BudgetLineDto(null, UUID.randomUUID(), "Line", 100, 0)
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("only for DRAFT budgets");
    }

    @Test
    void addLineRequiresPositivePlannedAmount() {
        UUID budgetId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceBudget existing = budget(budgetId, departmentId, BudgetStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(existing));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.addLine(
                budgetId,
                new BudgetLineDto(null, UUID.randomUUID(), "Line", 0, 0)
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("must be positive");
    }

    private MaintenanceBudget budget(UUID id, UUID departmentId, BudgetStatus status) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(id);
        budget.setYear(2026);
        budget.setMonth(5);
        budget.setDepartmentId(departmentId);
        budget.setStatus(status);
        return budget;
    }
}
