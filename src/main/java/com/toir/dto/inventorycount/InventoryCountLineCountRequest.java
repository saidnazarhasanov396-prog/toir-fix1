package com.toir.dto.inventorycount;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryCountLineCountRequest(
        BigDecimal countedQty,
        UUID countedById,
        String varianceReason
) {
}
