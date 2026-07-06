package com.toir.dto.workorder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record WorkOrderSparePartRequirementRequest(
        @NotNull UUID sparePartId,
        @Positive double requiredQty,
        String unit,
        String criticality,
        String notes
) {
}
