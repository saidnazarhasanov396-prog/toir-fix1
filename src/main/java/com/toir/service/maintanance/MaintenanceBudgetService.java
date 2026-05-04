package com.toir.service.maintanance;
import com.toir.entity.projects.BudgetLine;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetStatus;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.exception.RestException;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MaintenanceBudgetService {

    private final MaintenanceBudgetRepository repository;
    private final BudgetLineRepository lineRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;



    @Transactional(readOnly = true)
    public List<MaintenanceBudgetDto> findByYear(int year) {
        return repository.findAllByYearAndIsDeletedFalse(year).stream().map(MaintenanceBudgetDto::from).toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceBudgetDto findById(UUID id) {
        return MaintenanceBudgetDto.from(getOrThrow(id));
    }

    public MaintenanceBudgetDto create(MaintenanceBudgetDto r) {
        MaintenanceBudget b = new MaintenanceBudget();
        b.setYear(r.year());
        b.setMonth(r.month());
        b.setDepartmentId(r.departmentId());
        MaintenanceBudget saved = repository.save(b);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return MaintenanceBudgetDto.from(saved);
    }

    public MaintenanceBudgetDto approve(UUID id) {
        MaintenanceBudget b = getOrThrow(id);
        if (b.getStatus() != BudgetStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT budgets can be approved");
        }
        String oldJson = auditSerializationService.toJson(b);
        b.setStatus(BudgetStatus.APPROVED);
        audit(AuditAction.UPDATE, b.getId(), oldJson, b);
        return MaintenanceBudgetDto.from(b);
    }

    public BudgetLineDto addLine(UUID budgetId, BudgetLineDto r) {
        MaintenanceBudget b = getOrThrow(budgetId);
        if (b.getStatus() == BudgetStatus.LOCKED || b.getStatus() == BudgetStatus.CLOSED) {
            throw RestException.badRequest("Cannot add lines to locked/closed budget");
        }
        String oldJson = auditSerializationService.toJson(b);
        BudgetLine line = new BudgetLine();
        line.setBudget(b);
        line.setCostCategoryId(r.costCategoryId());
        line.setDescription(r.description());
        line.setPlannedAmount(r.plannedAmount());
        b.setTotalPlanned(b.getTotalPlanned() + r.plannedAmount());
        b.getLines().add(line);
        BudgetLine saved = lineRepository.save(line);
        audit(AuditAction.UPDATE, b.getId(), oldJson, b);
        return BudgetLineDto.from(saved);
    }

    private MaintenanceBudget getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Budget not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, MaintenanceBudget current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "maintenance_budget",
                id != null ? id.toString() : null,
                action,
                AuditModule.MAINTENANCE_BUDGET,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Бюджет обслуживания создан";
            case UPDATE -> "Бюджет обслуживания обновлен";
            case DELETE -> "Бюджет обслуживания удален";
            default -> "Действие выполнено над бюджетом обслуживания";
        };
    }
}
