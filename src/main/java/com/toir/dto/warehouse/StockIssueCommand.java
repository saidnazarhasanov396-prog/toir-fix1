package com.toir.dto.warehouse;

import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockIssueCommand(
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        BigDecimal quantity,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        String referenceType,
        UUID referenceId,
        String referenceDocNo,
        String notes,
        String idempotencyKey
) {
    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}
