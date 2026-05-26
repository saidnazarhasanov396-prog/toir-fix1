package com.toir.dto.maintenanceaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record MaintenanceActionRequest(
        @NotBlank String code,
        @NotBlank String name,
        String category,
        @PositiveOrZero Double defaultDurationHours,
        String requiredSkill,
        String safetyNotes,
        String toolsRequired,
        String sparePartsRequired,
        String consumablesRequired,
        Boolean active
) {}
