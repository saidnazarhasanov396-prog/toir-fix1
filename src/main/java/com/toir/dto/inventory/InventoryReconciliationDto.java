package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReconciliationDto(
        UUID sparePartId,
        String sparePartName,
        UUID warehouseId,
        String warehouseName,
        BigDecimal systemQuantity,
        BigDecimal actualQuantity,
        BigDecimal variance,
        LocalDate lastAdjustmentDate
) {
}
