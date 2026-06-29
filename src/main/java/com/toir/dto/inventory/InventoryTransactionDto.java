package com.toir.dto.inventory;

import com.toir.enums.InventoryTransactionType;
import com.toir.enums.InventoryAdjustmentReason;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryTransactionDto(
        UUID id,
        InventoryTransactionType type,
        UUID warehouseId,
        String warehouseName,
        UUID destinationWarehouseId,
        String destinationWarehouseName,
        UUID sparePartId,
        String sparePartName,
        BigDecimal quantity,
        BigDecimal actualQuantity,
        BigDecimal variance,
        InventoryAdjustmentReason adjustmentReason,
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
        UUID createdBy,
        UUID binId,
        UUID sourceBinId,
        UUID destinationBinId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        String sourceType,
        UUID sourceId
) {
    public InventoryTransactionDto(UUID id,
                                   InventoryTransactionType type,
                                   UUID warehouseId,
                                   String warehouseName,
                                   UUID destinationWarehouseId,
                                   String destinationWarehouseName,
                                   UUID sparePartId,
                                   String sparePartName,
                                   BigDecimal quantity,
                                   BigDecimal actualQuantity,
                                   BigDecimal variance,
                                   InventoryAdjustmentReason adjustmentReason,
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
                                   UUID createdBy) {
        this(id, type, warehouseId, warehouseName, destinationWarehouseId, destinationWarehouseName,
                sparePartId, sparePartName, quantity, actualQuantity, variance, adjustmentReason, unit,
                unitPrice, totalAmount, supplierName, takenById, takenByName, responsiblePersonId,
                responsiblePersonName, departmentId, departmentName, workOrderId, workOrderNumber,
                transactionDate, documentNumber, comment, createdAt, createdBy,
                null, null, null, null, null, null, null, null, null);
    }
}
