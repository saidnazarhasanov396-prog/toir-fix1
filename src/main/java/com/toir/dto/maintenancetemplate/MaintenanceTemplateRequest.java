package com.toir.dto.maintenancetemplate;

import com.toir.enums.MaintenanceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record MaintenanceTemplateRequest(
        String code,
        @NotBlank String name,
        String description,
        UUID equipmentTypeId,
        @NotNull MaintenanceKind maintenanceKind,
        @PositiveOrZero double normativeLaborHours,
        Boolean active,
        List<UUID> equipmentTypeIds
) {
    public MaintenanceTemplateRequest(
            String code,
            String name,
            String description,
            UUID equipmentTypeId,
            MaintenanceKind maintenanceKind,
            double normativeLaborHours,
            Boolean active
    ) {
        this(code, name, description, equipmentTypeId, maintenanceKind, normativeLaborHours, active, null);
    }
}
