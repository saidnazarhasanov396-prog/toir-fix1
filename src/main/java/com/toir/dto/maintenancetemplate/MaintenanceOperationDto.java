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
        UUID specialistId,
        String specialistName,
        @Positive Integer sequence,
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
    public MaintenanceOperationDto(
            UUID id,
            UUID actionId,
            String actionCode,
            String actionName,
            Integer sequence,
            String name,
            String description,
            double durationHours,
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
        this(id, actionId, actionCode, actionName, null, null, sequence, name, description, durationHours,
                requiredSkill, safetyNotes, toolsRequired, sparePartsRequired, consumablesRequired, controlParameter,
                controlUnit, controlMin, controlMax, instructionUrl);
    }

    public static MaintenanceOperationDto from(MaintenanceOperation o) {
        return new MaintenanceOperationDto(
                o.getId(),
                o.getAction() != null ? o.getAction().getId() : null,
                o.getAction() != null ? o.getAction().getCode() : null,
                o.getAction() != null ? o.getAction().getName() : null,
                o.getSpecialistId(),
                null,
                o.getSequence(), o.getName(), o.getDescription(),
                durationHoursOrZero(o.getDurationHours()), o.getRequiredSkill(), o.getSafetyNotes(),
                o.getToolsRequired(), o.getSparePartsRequired(), o.getConsumablesRequired(),
                o.getControlParameter(), o.getControlUnit(), o.getControlMin(), o.getControlMax(),
                o.getInstructionUrl());
    }

    public static double durationHoursOrZero(Double durationHours) {
        return durationHours == null ? 0 : durationHours;
    }
}
