package com.toir.dto.maintenancetemplate;

import com.toir.entity.maintenance.MaintenanceOperation;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record MaintenanceOperationDto(
        UUID id,
        UUID actionId,
        String actionCode,
        String actionName,
        @Positive int sequence,
        String name,
        String description,
        @PositiveOrZero double durationHours,
        String requiredSkill,
        String safetyNotes,
        String toolsRequired,
        String sparePartsRequired,
        String consumablesRequired,
        String controlParameter,
        String controlUnit,
        Double controlMin,
        Double controlMax,
        String instructionUrl
) {
    public static MaintenanceOperationDto from(MaintenanceOperation o) {
        return new MaintenanceOperationDto(
                o.getId(),
                o.getAction() != null ? o.getAction().getId() : null,
                o.getAction() != null ? o.getAction().getCode() : null,
                o.getAction() != null ? o.getAction().getName() : null,
                o.getSequence(), o.getName(), o.getDescription(),
                o.getDurationHours(), o.getRequiredSkill(), o.getSafetyNotes(),
                o.getToolsRequired(), o.getSparePartsRequired(), o.getConsumablesRequired(),
                o.getControlParameter(), o.getControlUnit(), o.getControlMin(), o.getControlMax(),
                o.getInstructionUrl());
    }
}
