package com.toir.dto.budget;

import com.toir.entity.projects.BudgetLine;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record BudgetLineDto(
        UUID id,
        @NotNull UUID costCategoryId,
        String description,
        @Positive double plannedAmount,
        double actualAmount
) {
    public static BudgetLineDto from(BudgetLine l) {
        return new BudgetLineDto(l.getId(), l.getCostCategoryId(), l.getDescription(),
                l.getPlannedAmount(), l.getActualAmount());
    }
}
