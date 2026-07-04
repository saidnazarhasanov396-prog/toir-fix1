package com.toir.dto.rcm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
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
        RcmFailureForecastDto failureForecast,
        RiskExplanationDto explanation,
        List<RiskReasonDto> reasons
) {
    public EquipmentRiskScore {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

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
                consequence, probability, riskScore, repairPriority, openDefects, mtbfHours, mttrHours, null, null, List.of());
    }

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
                              double mttrHours,
                              RiskExplanationDto explanation) {
        this(equipmentId, equipmentCode, equipmentName, criticalityClass, criticalityClassName,
                consequence, probability, riskScore, repairPriority, openDefects, mtbfHours, mttrHours,
                null, explanation, explanation == null ? List.of() : explanation.reasons());
    }

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
                              double mttrHours,
                              RcmFailureForecastDto failureForecast,
                              RiskExplanationDto explanation) {
        this(equipmentId, equipmentCode, equipmentName, criticalityClass, criticalityClassName,
                consequence, probability, riskScore, repairPriority, openDefects, mtbfHours, mttrHours,
                failureForecast, explanation, explanation == null ? List.of() : explanation.reasons());
    }

    @JsonProperty("probabilityPercent")
    public int probabilityPercent() {
        return probability * 20;
    }
}
