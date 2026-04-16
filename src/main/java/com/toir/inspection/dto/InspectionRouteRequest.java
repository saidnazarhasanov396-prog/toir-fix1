package com.toir.inspection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record InspectionRouteRequest(
        @NotBlank String code,
        @NotBlank String name,
        UUID departmentId,
        String frequency,
        Integer targetDurationMin,
        String description,
        Boolean active,
        @Valid List<CheckpointRequest> checkpoints
) {
    public record CheckpointRequest(
            int orderIndex,
            UUID equipmentId,
            UUID locationId,
            @NotBlank String title,
            String instruction,
            String checkType,
            Double expectedMin,
            Double expectedMax,
            String expectedUnit,
            Boolean mandatory
    ) {}
}
