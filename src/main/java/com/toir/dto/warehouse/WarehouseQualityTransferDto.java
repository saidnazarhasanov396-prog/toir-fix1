package com.toir.dto.warehouse;

import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record WarehouseQualityTransferDto(
        UUID operationId,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        WarehouseStockStatus fromStatus,
        WarehouseStockStatus toStatus,
        BigDecimal quantity,
        String status
) {
}
