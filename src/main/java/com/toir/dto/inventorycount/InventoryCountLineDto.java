package com.toir.dto.inventorycount;

import com.toir.enums.InventoryCountLineStatus;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryCountLineDto(
        UUID id,
        UUID warehouseId,
        UUID binId,
        UUID sparePartId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        BigDecimal expectedQty,
        BigDecimal countedQty,
        BigDecimal varianceQty,
        String unit,
        InventoryCountLineStatus status,
        UUID countedById,
        Instant countedAt,
        String varianceReason
) {
}
