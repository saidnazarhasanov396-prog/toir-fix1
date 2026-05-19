package com.toir.dto.meter;

public record MeterStatsResponse(
        long totalMeters,
        long activeMeters,
        long totalReadings,
        long dueTriggers
) {
}
