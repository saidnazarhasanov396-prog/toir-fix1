package com.toir.inspection.dto;

import com.toir.inspection.InspectionCheckpoint;

import java.util.UUID;

public record InspectionCheckpointDto(
        UUID id,
        UUID routeId,
        int orderIndex,
        UUID equipmentId,
        UUID locationId,
        String title,
        String instruction,
        String checkType,
        Double expectedMin,
        Double expectedMax,
        String expectedUnit,
        boolean mandatory
) {
    public static InspectionCheckpointDto from(InspectionCheckpoint c) {
        return new InspectionCheckpointDto(
                c.getId(),
                c.getRoute() != null ? c.getRoute().getId() : null,
                c.getOrderIndex(),
                c.getEquipmentId(),
                c.getLocationId(),
                c.getTitle(),
                c.getInstruction(),
                c.getCheckType(),
                c.getExpectedMin(),
                c.getExpectedMax(),
                c.getExpectedUnit(),
                c.isMandatory()
        );
    }
}
