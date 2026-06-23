package com.toir.dto.budget;

import java.util.List;
import java.util.UUID;

public record BudgetSummaryResponse(
        List<Item> items,
        List<Item> budgets,
        double totalPlanned,
        double totalActual,
        double totalRemaining,
        double variance,
        double executionPercent,
        long budgetCount,
        List<CategoryRow> byCategory
) {
    public record Item(
            UUID id,
            int year,
            Integer month,
            String status,
            double totalPlanned,
            double totalActual,
            DepartmentRef department
    ) {
    }

    public record DepartmentRef(UUID id, String code, String name) {
    }

    public record CategoryRef(UUID id, String code, String name) {
    }

    public record CategoryRow(
            CategoryRef category,
            double plannedAmount,
            double actualAmount,
            double variance
    ) {
    }
}
