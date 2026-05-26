package com.toir.dto.pprplanning;

import com.toir.entity.PprPlanTarget;
import com.toir.enums.PprTargetType;

import java.util.UUID;

public record PprPlanTargetDto(
        UUID id,
        PprTargetType targetType,
        UUID equipmentId,
        UUID equipmentTypeId
) {
    public static PprPlanTargetDto from(PprPlanTarget target) {
        return new PprPlanTargetDto(
                target.getId(),
                target.getTargetType(),
                target.getEquipmentId(),
                target.getEquipmentTypeId()
        );
    }
}
