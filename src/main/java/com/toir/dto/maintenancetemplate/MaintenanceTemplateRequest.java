package com.toir.dto.maintenancetemplate;

import com.toir.enums.MaintenanceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record MaintenanceTemplateRequest(
        String code,
        @NotBlank String name,
        String description,
        @NotNull UUID equipmentTypeId,
        @NotNull MaintenanceKind maintenanceKind,
        @PositiveOrZero double normativeLaborHours,
        Boolean active
) {}
