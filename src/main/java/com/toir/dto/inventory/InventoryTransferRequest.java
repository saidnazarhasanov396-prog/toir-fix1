package com.toir.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryTransferRequest(
        @NotNull UUID sourceWarehouseId,
        @NotNull UUID destinationWarehouseId,
        @NotNull UUID sparePartId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotBlank String unit,
        LocalDate transferDate,
        @NotNull UUID responsiblePersonId,
        String documentNumber,
        String comment
) {
}
