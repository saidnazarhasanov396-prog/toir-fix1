package com.toir.dto.warehouse;

import java.math.BigDecimal;
import java.util.UUID;

public record StockIssueCommand(
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        BigDecimal quantity,
        String lotNumber,
        String serialNumber,
        String referenceType,
        UUID referenceId,
        String referenceDocNo,
        String notes,
        String idempotencyKey
) {
}
