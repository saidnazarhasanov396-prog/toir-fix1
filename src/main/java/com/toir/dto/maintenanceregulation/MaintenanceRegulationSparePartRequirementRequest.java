package com.toir.dto.maintenanceregulation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import java.math.BigDecimal;

public record MaintenanceRegulationSparePartRequirementRequest(
        @NotNull UUID sparePartId,
        @Positive @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class) BigDecimal quantity,
        String unit,
        String criticality,
        String notes,
        Boolean active
) {
}
