package com.toir.dto.workorder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import java.math.BigDecimal;

public record WorkOrderSparePartRequirementRequest(
        @NotNull UUID sparePartId,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.toir.dto.common.MoneyDecimalStringDeserializer.class)
        @Positive @jakarta.validation.constraints.Digits(integer=15,fraction=4) BigDecimal requiredQty,
        String unit,
        String criticality,
        String notes
) {
}
