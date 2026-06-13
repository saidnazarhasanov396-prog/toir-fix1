package com.toir.dto.inventory;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.StockoutRiskLevel;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryStockoutRiskDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        BigDecimal availableQuantity,
        BigDecimal averageDailyConsumption,
        Integer leadTimeDays,
        BigDecimal safetyStock,
        BigDecimal daysRemaining,
        BigDecimal expectedConsumption,
        StockoutRiskLevel riskLevel,
        CriticalityLevel criticality
) {
}
