package com.toir.dto.rcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.toir.dto.analytics.MetricExplanationDto;

import java.util.UUID;

/**
 * DTO representing current calculated risk for a piece of equipment.
 */
public record EquipmentRiskScore(
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        String criticalityClass,
        String criticalityClassName,
        int consequence,
        int probability,
        int riskScore,
        Integer repairPriority,
        long openDefects,
        double mtbfHours,
        double mttrHours,
        MetricExplanationDto explanation
) {
    public EquipmentRiskScore(UUID equipmentId,
                              String equipmentCode,
                              String equipmentName,
                              String criticalityClass,
                              String criticalityClassName,
                              int consequence,
                              int probability,
                              int riskScore,
                              Integer repairPriority,
                              long openDefects,
                              double mtbfHours,
                              double mttrHours) {
        this(equipmentId, equipmentCode, equipmentName, criticalityClass, criticalityClassName,
                consequence, probability, riskScore, repairPriority, openDefects, mtbfHours, mttrHours, null);
    }

    @JsonProperty("probabilityPercent")
    public int probabilityPercent() {
        return probability * 20;
    }
}
