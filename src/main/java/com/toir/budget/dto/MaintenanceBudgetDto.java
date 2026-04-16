package com.toir.budget.dto;

import com.toir.budget.BudgetStatus;
import com.toir.budget.MaintenanceBudget;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record MaintenanceBudgetDto(
        UUID id,
        @NotNull @Min(2000) Integer year,
        Integer month,
        UUID departmentId,
        BudgetStatus status,
        double totalPlanned,
        double totalActual,
        List<BudgetLineDto> lines
) {
    public static MaintenanceBudgetDto from(MaintenanceBudget b) {
        return new MaintenanceBudgetDto(b.getId(), b.getYear(), b.getMonth(), b.getDepartmentId(),
                b.getStatus(), b.getTotalPlanned(), b.getTotalActual(),
                b.getLines().stream().map(BudgetLineDto::from).toList());
    }
}
