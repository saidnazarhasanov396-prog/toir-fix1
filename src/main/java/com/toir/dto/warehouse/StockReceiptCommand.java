package com.toir.dto.warehouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockReceiptCommand(
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        BigDecimal quantity,
        BigDecimal unitCost,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        String referenceType,
        UUID referenceId,
        String referenceDocNo,
        String notes,
        String idempotencyKey
) {
}
