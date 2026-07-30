package com.toir.dto.sparepartlifecycle;

import com.toir.enums.EquipmentStatus;
import com.toir.enums.sparepartlifecycle.SparePartOperationalReadiness;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartOperationalReadinessDto(
        UUID equipmentId,
        EquipmentStatus baseEquipmentStatus,
        SparePartOperationalReadiness operationalReadiness,
        boolean hasEvaluationError,
        int evaluationErrorCount,
        List<SparePartReadinessReason> reasons,
        Instant evaluatedAt
) {
    public SparePartOperationalReadinessDto(UUID equipmentId, EquipmentStatus baseEquipmentStatus,
                                            SparePartOperationalReadiness operationalReadiness,
                                            List<SparePartReadinessReason> reasons, Instant evaluatedAt) {
        this(equipmentId, baseEquipmentStatus, operationalReadiness, false, 0, reasons, evaluatedAt);
    }
}
