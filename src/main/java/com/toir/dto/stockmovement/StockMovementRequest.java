package com.toir.dto.stockmovement;

import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

public record StockMovementRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        UUID workOrderId,
        @NotNull StockMovementType type,
        @Positive @jakarta.validation.constraints.Digits(integer=15,fraction=4) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class) BigDecimal quantity,
        Double unitCost,
        String documentNumber,
        UUID createdById,
        String notes,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public StockMovementRequest(UUID warehouseId,
                                UUID sparePartId,
                                UUID workOrderId,
                                StockMovementType type,
                                BigDecimal quantity,
                                Double unitCost,
                                String documentNumber,
                                UUID createdById,
                                String notes) {
        this(warehouseId, sparePartId, workOrderId, type, quantity, unitCost, documentNumber, createdById,
                notes, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
