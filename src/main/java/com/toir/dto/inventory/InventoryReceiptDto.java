package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReceiptDto(
        UUID id,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartName,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String supplierName,
        UUID responsiblePersonId,
        String responsiblePersonName,
        LocalDate receiptDate,
        String documentNumber,
        String comment
) {
}
