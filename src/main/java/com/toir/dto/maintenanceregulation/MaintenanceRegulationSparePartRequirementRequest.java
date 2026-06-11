package com.toir.dto.maintenanceregulation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record MaintenanceRegulationSparePartRequirementRequest(
        @NotNull UUID sparePartId,
        @Positive double quantity,
        String unit,
        String criticality,
        String notes,
        Boolean active
) {
}
