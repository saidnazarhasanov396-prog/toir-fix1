package com.toir.service;
import com.toir.entity.BudgetLine;
import com.toir.enums.BudgetStatus;
import com.toir.entity.MaintenanceBudget;
import com.toir.repository.BudgetLineRepository;
import com.toir.repository.MaintenanceBudgetRepository;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.exception.RestException;
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



    @Transactional(readOnly = true)
    public List<MaintenanceBudgetDto> findByYear(int year) {
        return repository.findAllByYear(year).stream().map(MaintenanceBudgetDto::from).toList();
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
        return MaintenanceBudgetDto.from(repository.save(b));
    }

    public MaintenanceBudgetDto approve(UUID id) {
        MaintenanceBudget b = getOrThrow(id);
        if (b.getStatus() != BudgetStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT budgets can be approved");
        }
        b.setStatus(BudgetStatus.APPROVED);
        return MaintenanceBudgetDto.from(b);
    }

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
        return BudgetLineDto.from(lineRepository.save(line));
    }

    private MaintenanceBudget getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Budget not found: " + id));
    }
}
