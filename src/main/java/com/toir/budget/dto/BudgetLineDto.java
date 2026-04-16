package com.toir.budget.dto;

import com.toir.budget.BudgetLine;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record BudgetLineDto(
        UUID id,
        @NotNull UUID costCategoryId,
        String description,
        @PositiveOrZero double plannedAmount,
        double actualAmount
) {
    public static BudgetLineDto from(BudgetLine l) {
        return new BudgetLineDto(l.getId(), l.getCostCategoryId(), l.getDescription(),
                l.getPlannedAmount(), l.getActualAmount());
    }
}
