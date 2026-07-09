package com.toir.dto.budget;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FinanceDashboardResponse(
        double totalPlanned,
        double totalCommitted,
        double approvedActual,
        double pendingActual,
        double rejectedActual,
        double remainingBudget,
        double forecastRemaining,
        double variance,
        double burnRate,
        double riskAmount,
        double unallocatedAmount,
        Instant generatedAt,
        Filters filters,
        List<FinanceReportRow> byDepartment,
        List<FinanceReportRow> byCategory
) {
    public record Filters(Integer year, Integer month, UUID departmentId) {
    }
}
