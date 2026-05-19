package com.toir.repository.equipment;

public interface MeterStatsProjection {
    Long getTotalMeters();
    Long getActiveMeters();
    Long getTotalReadings();
    Long getDueTriggers();
}
