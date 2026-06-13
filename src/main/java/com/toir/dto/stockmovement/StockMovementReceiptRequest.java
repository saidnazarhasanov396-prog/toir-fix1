package com.toir.dto.stockmovement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockMovementReceiptRequest(
        @NotNull UUID sparePartId,
        @NotNull UUID warehouseId,
        @Positive double quantity,
        @NotBlank String unit,
        @Positive BigDecimal unitPrice,
        LocalDate receivedAt,
        UUID responsiblePersonId,
        String supplierName,
        String documentNumber,
        String comment
) {}
