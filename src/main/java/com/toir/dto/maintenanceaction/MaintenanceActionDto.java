package com.toir.dto.maintenanceaction;

import com.toir.entity.maintenance.MaintenanceAction;

import java.util.UUID;

public record MaintenanceActionDto(
        UUID id,
        String code,
        String name,
        String category,
        Double defaultDurationHours,
        String requiredSkill,
        String safetyNotes,
        String toolsRequired,
        String sparePartsRequired,
        String consumablesRequired,
        boolean active
) {
    public static MaintenanceActionDto from(MaintenanceAction action) {
        return new MaintenanceActionDto(
                action.getId(),
                action.getCode(),
                action.getName(),
                action.getCategory(),
                action.getDefaultDurationHours(),
                action.getRequiredSkill(),
                action.getSafetyNotes(),
                action.getToolsRequired(),
                action.getSparePartsRequired(),
                action.getConsumablesRequired(),
                action.isActive()
        );
    }
}
