package com.toir.dto.inventory;

import java.math.BigDecimal;

public record InventoryKpiDto(
        BigDecimal inventoryValue,
        long fastMovingCount,
        long slowMovingCount,
        long deadStockCount,
        long criticalItems,
        long stockoutRisks,
        long abcAItems,
        long xyzZItems
) {
}
