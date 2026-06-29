package com.toir.dto.warehouse;

import java.math.BigDecimal;
import java.util.UUID;

public record WarehouseStockMoveResponse(
        UUID stockMovementId,
        UUID moveOutLedgerId,
        UUID moveInLedgerId,
        UUID warehouseId,
        UUID sparePartId,
        UUID fromBinId,
        UUID toBinId,
        BigDecimal quantity
) {
}
