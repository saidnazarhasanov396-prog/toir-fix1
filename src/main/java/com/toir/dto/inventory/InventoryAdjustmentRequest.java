package com.toir.dto.inventory;

import com.toir.enums.InventoryAdjustmentReason;
import com.toir.enums.WarehouseStockStatus;
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
        String comment,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public InventoryAdjustmentRequest(UUID warehouseId,
                                      UUID sparePartId,
                                      BigDecimal actualQuantity,
                                      InventoryAdjustmentReason reason,
                                      UUID responsiblePersonId,
                                      LocalDate adjustmentDate,
                                      String documentNumber,
                                      String comment) {
        this(warehouseId, sparePartId, actualQuantity, reason, responsiblePersonId, adjustmentDate,
                documentNumber, comment, null, null, null, null, null);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
