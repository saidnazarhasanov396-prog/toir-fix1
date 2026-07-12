package com.toir.dto.maintenancetemplate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;
import java.math.BigDecimal;

public record MaintenanceTemplateSparePartRequirementRequest(
        UUID operationId,
        @NotNull UUID sparePartId,
        @Positive @jakarta.validation.constraints.Digits(integer=15,fraction=4) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class) BigDecimal quantity,
        String unit,
        String criticality,
        String notes,
        Boolean active
) {
}
