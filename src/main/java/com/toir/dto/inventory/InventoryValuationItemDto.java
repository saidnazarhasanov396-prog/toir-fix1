package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryValuationItemDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        BigDecimal availableQuantity,
        BigDecimal averageCost,
        BigDecimal inventoryValue
) {
}
