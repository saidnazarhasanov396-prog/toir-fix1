package com.toir.service.maintanance;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceBudgetService {

    private final MaintenanceBudgetRepository repository;
    private final BudgetLineRepository lineRepository;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public List<MaintenanceBudgetDto> findByYear(int year) {
        if (scopeAccessService.isScopeAdmin()) {
            return repository.findAllByYearAndIsDeletedFalse(year).stream().map(MaintenanceBudgetDto::from).toList();
        }
        UUID departmentId = scopeAccessService.enforceDepartmentScope(null);
        if (departmentId == null) {
            throw forbidden();
        }
        return repository.findAllByDepartmentIdAndYearAndIsDeletedFalse(departmentId, year).stream()
                .map(MaintenanceBudgetDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceBudgetDto findById(UUID id) {
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        return MaintenanceBudgetDto.from(budget);
    }

    @Transactional
    public MaintenanceBudgetDto create(MaintenanceBudgetDto r) {
        assertCanCreateBudget(r.departmentId());
        MaintenanceBudget b = new MaintenanceBudget();
        b.setYear(r.year());
        b.setMonth(r.month());
        b.setDepartmentId(r.departmentId());
        MaintenanceBudget saved = repository.save(b);

        auditBuilderService.log(
                "maintenance_budget",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_BUDGET,
                "Бюджет обслуживания создан",
                null,
                saved
        );

        return MaintenanceBudgetDto.from(saved);
    }

    @Transactional
    public MaintenanceBudgetDto approve(UUID id) {
        MaintenanceBudget b = getOrThrow(id);
        assertCanAccessBudget(b);
        if (b.getStatus() != BudgetStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT budgets can be approved");
        }
        b.setStatus(BudgetStatus.APPROVED);
        MaintenanceBudget save = repository.save(b);

        auditBuilderService.log(
                "maintenance_budget",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_BUDGET,
                "Бюджет обслуживания обновлен",
                b,
                save
        );
        return MaintenanceBudgetDto.from(save);
    }

    @Transactional
    public BudgetLineDto addLine(UUID budgetId, BudgetLineDto r) {
        MaintenanceBudget b = getOrThrow(budgetId);
        assertCanAccessBudget(b);
        if (b.getStatus() == BudgetStatus.LOCKED || b.getStatus() == BudgetStatus.CLOSED) {
            throw RestException.badRequest("Cannot add lines to locked/closed budget");
        }
        BudgetLine line = new BudgetLine();
        line.setBudget(b);
        line.setCostCategoryId(r.costCategoryId());
        line.setDescription(r.description());
        line.setPlannedAmount(r.plannedAmount());
        b.setTotalPlanned(b.getTotalPlanned() + r.plannedAmount());
        b.getLines().add(line);

        BudgetLine budgetLine = lineRepository.save(line);

        MaintenanceBudget maintenanceBudget = repository.save(b);

        auditBuilderService.log(
                "maintenance_budget",
                maintenanceBudget.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_BUDGET,
                "Бюджет обслуживания обновлен",
                b,
                maintenanceBudget
        );

        return BudgetLineDto.from(budgetLine);
    }

    private MaintenanceBudget getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Budget not found: " + id));
    }

    private void assertCanCreateBudget(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw forbidden();
        }
    }

    private void assertCanAccessBudget(MaintenanceBudget budget) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (budget.getDepartmentId() == null || !scopeAccessService.canAccessDepartment(budget.getDepartmentId())) {
            throw forbidden();
        }
    }

    private AccessDeniedException forbidden() {
        return new AccessDeniedException("Access denied by budget scope");
    }
}
