package com.toir.dto.inventory;

import com.toir.enums.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryTransactionDto(
        UUID id,
        InventoryTransactionType type,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartName,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String supplierName,
        UUID takenById,
        String takenByName,
        UUID responsiblePersonId,
        String responsiblePersonName,
        UUID departmentId,
        String departmentName,
        UUID workOrderId,
        String workOrderNumber,
        LocalDate transactionDate,
        String documentNumber,
        String comment,
        LocalDateTime createdAt,
        UUID createdBy
) {
}
