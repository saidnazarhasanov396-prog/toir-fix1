package com.toir.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReturnRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotNull UUID workOrderId,
        @NotNull UUID returnedById,
        @NotNull UUID responsiblePersonId,
        LocalDate returnDate,
        String documentNumber,
        String comment
) {
}
