package com.toir.dto.analytics;

import com.toir.enums.DowntimeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentAnalyticsResponse(
        String equipmentId,
        double mtbfHours,
        double mttrHours,
        double availability,
        long downtimeMinutes,
        List<HistoryRow> history,
        List<DowntimeRow> downtimes,
        List<DowntimeRow> events
) {
    public EquipmentAnalyticsResponse {
        history = history == null ? List.of() : List.copyOf(history);
        downtimes = downtimes == null ? List.of() : List.copyOf(downtimes);
        events = events == null ? List.of() : List.copyOf(events);
    }

    public record HistoryRow(
            LocalDate metricDate,
            double mtbfHours,
            double mttrHours,
            double availability
    ) {
    }

    public record DowntimeRow(
            UUID id,
            Instant startAt,
            Instant endAt,
            Integer durationMinutes,
            DowntimeType type,
            String description
    ) {
    }
}
