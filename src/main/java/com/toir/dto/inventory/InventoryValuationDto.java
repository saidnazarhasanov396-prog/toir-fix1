package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.util.List;

public record InventoryValuationDto(
        BigDecimal totalInventoryValue,
        long totalItems,
        BigDecimal totalStockQuantity,
        List<InventoryValuationItemDto> topValueItems
) {
}
