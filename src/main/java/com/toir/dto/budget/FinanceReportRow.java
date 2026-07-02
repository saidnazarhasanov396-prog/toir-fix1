package com.toir.dto.budget;

import java.util.UUID;

public record FinanceReportRow(
        UUID groupId,
        String groupCode,
        String groupName,
        String groupType,
        double plannedAmount,
        double committedAmount,
        double approvedActualAmount,
        double pendingActualAmount,
        double rejectedActualAmount,
        double remainingBudget,
        double forecastRemaining,
        double variance,
        double burnRate,
        double riskAmount,
        double unallocatedAmount,
        long actualCostCount
) {
}
