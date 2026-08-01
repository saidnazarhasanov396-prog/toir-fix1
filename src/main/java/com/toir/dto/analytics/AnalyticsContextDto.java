package com.toir.dto.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalyticsContextDto(
        AnalyticsPeriod period,
        Instant from,
        Instant to,
        String timezone,
        Scope scope,
        Instant calculatedAt,
        List<String> sources
) {
    public AnalyticsContextDto {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Scope(String type, UUID departmentId, String departmentName) {
    }
}
