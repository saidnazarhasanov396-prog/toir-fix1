package com.toir.dto.budget;

import java.util.List;
import java.util.UUID;

public record BudgetSummaryResponse(
        List<Item> items,
        double totalPlanned,
        double totalActual,
        double totalRemaining,
        double variance,
        double executionPercent,
        List<CategoryRow> byCategory
) {
    public record Item(
            UUID id,
            int year,
            int month,
            double totalPlanned,
            double totalActual
    ) {
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
