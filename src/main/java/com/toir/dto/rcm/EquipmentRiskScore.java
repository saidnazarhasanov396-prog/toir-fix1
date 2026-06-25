package com.toir.dto.rcm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * RCM-оценка риска единицы оборудования. По ТЗ §4.2.7/4.2.13 —
 * консолидирует impact-поля CriticalityClass и фактическую историю
 * отказов/простоев в единый индекс 0..100 (чем выше — тем критичнее).
 */
public record EquipmentRiskScore(
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        String criticalityClass,
        String criticalityClassName,
        /** Суммарный consequence 0..20 — из impact-полей CriticalityClass. */
        int consequence,
        /** Частота отказов на основании defect history + MTBF, 0..5. */
        int probability,
        /** Итог: consequence × probability, нормирован 0..100. */
        int riskScore,
        /** Ранг приоритета ремонта из класса критичности (1..5). */
        Integer repairPriority,
        long openDefects,
        double mtbfHours,
        double mttrHours
) {
        @JsonProperty("probabilityPercent")
        public int probabilityPercent() {
                return probability * 20;
        }
}
