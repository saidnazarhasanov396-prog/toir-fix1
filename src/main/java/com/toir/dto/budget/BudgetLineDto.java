package com.toir.dto.budget;

import com.toir.entity.projects.BudgetLine;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record BudgetLineDto(
        UUID id,
        @NotNull UUID costCategoryId,
        String costCategoryName,
        String description,
        @Positive double plannedAmount,
        double actualAmount
) {
    public BudgetLineDto(UUID id,
                         UUID costCategoryId,
                         String description,
                         double plannedAmount,
                         double actualAmount) {
        this(id, costCategoryId, null, description, plannedAmount, actualAmount);
    }

    public static BudgetLineDto from(BudgetLine l) {
        return from(l, null);
    }

    public static BudgetLineDto from(BudgetLine l, String costCategoryName) {
        return new BudgetLineDto(
                l.getId(),
                l.getCostCategoryId(),
                costCategoryName,
                l.getDescription(),
                l.getPlannedAmount(),
                l.getActualAmount()
        );
    }
}
