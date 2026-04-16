package com.toir.reliability.dto;

import com.toir.reliability.ReliabilityMetric;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ReliabilityMetricDto(
        UUID id,
        @NotNull UUID equipmentId,
        @NotNull LocalDate metricDate,
        Double mtbfHours,
        Double mttrHours,
        Double availability,
        Double failureRate
) {
    public static ReliabilityMetricDto from(ReliabilityMetric m) {
        return new ReliabilityMetricDto(m.getId(), m.getEquipmentId(), m.getMetricDate(),
                m.getMtbfHours(), m.getMttrHours(), m.getAvailability(), m.getFailureRate());
    }
}
