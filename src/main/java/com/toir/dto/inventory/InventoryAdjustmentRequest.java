package com.toir.dto.inventory;

import com.toir.enums.InventoryAdjustmentReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryAdjustmentRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0") BigDecimal actualQuantity,
        @NotNull InventoryAdjustmentReason reason,
        @NotNull UUID responsiblePersonId,
        LocalDate adjustmentDate,
        String documentNumber,
        String comment
) {
}
