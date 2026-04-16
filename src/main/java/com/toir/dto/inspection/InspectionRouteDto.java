package com.toir.dto.inspection;
import com.toir.dto.inspection.InspectionCheckpointDto;

import com.toir.entity.InspectionRoute;

import java.util.List;
import java.util.UUID;

public record InspectionRouteDto(
        UUID id,
        String code,
        String name,
        UUID departmentId,
        String frequency,
        Integer targetDurationMin,
        String description,
        boolean active,
        List<InspectionCheckpointDto> checkpoints
) {
    public static InspectionRouteDto from(InspectionRoute r) {
        return new InspectionRouteDto(
                r.getId(),
                r.getCode(),
                r.getName(),
                r.getDepartmentId(),
                r.getFrequency(),
                r.getTargetDurationMin(),
                r.getDescription(),
                r.isActive(),
                r.getCheckpoints() == null ? List.of()
                        : r.getCheckpoints().stream().map(InspectionCheckpointDto::from).toList()
        );
    }
}
