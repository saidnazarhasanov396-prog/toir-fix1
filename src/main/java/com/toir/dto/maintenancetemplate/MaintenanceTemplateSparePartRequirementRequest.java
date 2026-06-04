package com.toir.dto.maintenancetemplate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record MaintenanceTemplateSparePartRequirementRequest(
        UUID operationId,
        @NotNull UUID sparePartId,
        @Positive double quantity,
        String unit,
        String criticality,
        String notes,
        Boolean active
) {
}
