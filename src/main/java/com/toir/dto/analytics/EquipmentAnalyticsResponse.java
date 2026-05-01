package com.toir.dto.analytics;

import java.time.LocalDate;
import java.util.List;

public record EquipmentAnalyticsResponse(
        String equipmentId,
        double mtbfHours,
        double mttrHours,
        double availability,
        long downtimeMinutes,
        List<HistoryRow> history
) {
    public record HistoryRow(
            LocalDate metricDate,
            double mtbfHours,
            double mttrHours,
            double availability
    ) {
    }
}
