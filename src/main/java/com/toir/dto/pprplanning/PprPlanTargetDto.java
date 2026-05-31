package com.toir.dto.pprplanning;

import com.toir.entity.PprPlanTarget;
import com.toir.enums.PprTargetType;

import java.util.UUID;

public record PprPlanTargetDto(
        UUID id,
        PprTargetType targetType,
        UUID equipmentId,
        String equipmentName,
        UUID equipmentTypeId,
        String equipmentTypeName,
        UUID regulationId,
        String regulationName
) {
    public PprPlanTargetDto(
            UUID id,
            PprTargetType targetType,
            UUID equipmentId,
            UUID equipmentTypeId
    ) {
        this(id, targetType, equipmentId, null, equipmentTypeId, null, null, null);
    }

    public static PprPlanTargetDto from(PprPlanTarget target) {
        return from(target, null, null, null);
    }

    public static PprPlanTargetDto from(PprPlanTarget target,
                                        String equipmentName,
                                        String equipmentTypeName,
                                        String regulationName) {
        return new PprPlanTargetDto(
                target.getId(),
                target.getTargetType(),
                target.getEquipmentId(),
                equipmentName,
                target.getEquipmentTypeId(),
                equipmentTypeName,
                target.getRegulationId(),
                regulationName
        );
    }
}
