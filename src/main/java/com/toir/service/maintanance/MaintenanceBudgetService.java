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
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
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



    @Transactional(readOnly = true)
    public List<MaintenanceBudgetDto> findByYear(int year) {
        return repository.findAllByYearAndIsDeletedFalse(year).stream().map(MaintenanceBudgetDto::from).toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceBudgetDto findById(UUID id) {
        return MaintenanceBudgetDto.from(getOrThrow(id));
    }

    @Transactional
    public MaintenanceBudgetDto create(MaintenanceBudgetDto r) {
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
}
