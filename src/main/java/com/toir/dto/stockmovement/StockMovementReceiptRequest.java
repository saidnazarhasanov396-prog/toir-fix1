package com.toir.dto.stockmovement;

import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockMovementReceiptRequest(
        @NotNull UUID sparePartId,
        @NotNull UUID warehouseId,
        @Positive @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class) BigDecimal quantity,
        @NotBlank String unit,
        @Positive BigDecimal unitPrice,
        LocalDate receivedAt,
        UUID responsiblePersonId,
        String supplierName,
        String documentNumber,
        String comment,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public StockMovementReceiptRequest(UUID sparePartId,
                                       UUID warehouseId,
                                       BigDecimal quantity,
                                       String unit,
                                       BigDecimal unitPrice,
                                       LocalDate receivedAt,
                                       UUID responsiblePersonId,
                                       String supplierName,
                                       String documentNumber,
                                       String comment) {
        this(sparePartId, warehouseId, quantity, unit, unitPrice, receivedAt, responsiblePersonId,
                supplierName, documentNumber, comment, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
