package com.toir.dto.analytics;

import java.time.Instant;
import java.util.UUID;

public record AnalyticsDowntimeEventRow(
        UUID sourceId,
        String sourceType,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        String causeKey,
        Instant startAt,
        Instant endAt,
        long durationMinutes,
        boolean completed,
        String number,
        String title,
        String description
) {
}
