package com.toir.dto.stockmovement;

import com.toir.entity.StockMovement;
import com.toir.enums.SparePartType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.repository.StockMovementListRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockMovementDto(
        UUID id,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartName,
        SparePartType sparePartType,
        UUID workOrderId,
        String workOrderNumber,
        String workOrderName,
        StockMovementType type,
        double quantity,
        String unit,
        Double unitCost,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String documentNumber,
        StockMovementSourceType sourceType,
        UUID sourceId,
        UUID sourceLineId,
        UUID createdById,
        String createdByFullName,
        UUID responsiblePersonId,
        String responsiblePersonName,
        UUID takenById,
        String takenByName,
        UUID departmentId,
        String supplierName,
        LocalDate movementDate,
        Instant occurredAt,
        String notes,
        String comment,
        int fileCount,
        UUID equipmentTypeId,
        String equipmentTypeName
) {
    public StockMovementDto(
            UUID id,
            UUID warehouseId,
            String warehouseName,
            UUID sparePartId,
            String sparePartName,
            SparePartType sparePartType,
            UUID workOrderId,
            String workOrderNumber,
            String workOrderName,
            StockMovementType type,
            double quantity,
            String unit,
            Double unitCost,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String documentNumber,
            UUID createdById,
            String createdByFullName,
            UUID responsiblePersonId,
            String responsiblePersonName,
            UUID takenById,
            String takenByName,
            UUID departmentId,
            String supplierName,
            LocalDate movementDate,
            Instant occurredAt,
            String notes,
            String comment
    ) {
        this(id, warehouseId, warehouseName, sparePartId, sparePartName, sparePartType, workOrderId,
                workOrderNumber, workOrderName, type, quantity, unit, unitCost, unitPrice, totalAmount,
                documentNumber, null, null, null, createdById, createdByFullName, responsiblePersonId, responsiblePersonName,
                takenById, takenByName, departmentId, supplierName, movementDate, occurredAt, notes, comment, 0,
                null, null);
    }

    public StockMovementDto(
            UUID id,
            UUID warehouseId,
            String warehouseName,
            UUID sparePartId,
            String sparePartName,
            UUID workOrderId,
            String workOrderNumber,
            String workOrderName,
            StockMovementType type,
            double quantity,
            Double unitCost,
            String documentNumber,
            UUID createdById,
            String createdByFullName,
            Instant occurredAt,
            String notes
    ) {
        this(id, warehouseId, warehouseName, sparePartId, sparePartName, null, workOrderId,
                workOrderNumber, workOrderName, type, quantity, null, unitCost,
                unitPriceFromLegacy(unitCost), totalAmount(quantity, unitPriceFromLegacy(unitCost), null),
                documentNumber, null, null, null, createdById, createdByFullName, null, null, null, null,
                null, null, null, occurredAt, notes, notes, 0, null, null);
    }

    public static StockMovementDto from(StockMovement m) {
        BigDecimal unitPrice = unitPrice(m);
        return new StockMovementDto(
                m.getId(),
                m.getWarehouseId(),
                null,
                m.getSparePartId(),
                null,
                null,
                m.getWorkOrderId(),
                null,
                null,
                m.getType(),
                m.getQuantity(),
                m.getUnit(),
                m.getUnitCost(),
                unitPrice,
                totalAmount(m.getQuantity(), unitPrice, m.getTotalAmount()),
                m.getDocumentNumber(),
                m.getSourceType(),
                m.getSourceId(),
                m.getSourceLineId(),
                m.getCreatedById(),
                null,
                m.getResponsiblePersonId(),
                null,
                m.getTakenById(),
                null,
                m.getDepartmentId(),
                m.getSupplierName(),
                m.getMovementDate(),
                m.getOccurredAt(),
                m.getNotes(),
                m.getComment() != null ? m.getComment() : m.getNotes(),
                0,
                m.getEquipmentTypeId(),
                null
        );
    }

    public static StockMovementDto from(StockMovementListRow row) {
        BigDecimal unitPrice = row.getUnitPrice() != null ? row.getUnitPrice() : unitPriceFromLegacy(row.getUnitCost());
        return new StockMovementDto(
                row.getId(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getSparePartId(),
                row.getSparePartName(),
                parseSparePartType(row.getSparePartType()),
                row.getWorkOrderId(),
                row.getWorkOrderNumber(),
                row.getWorkOrderName(),
                row.getType() != null ? StockMovementType.valueOf(row.getType()) : null,
                row.getQuantity(),
                row.getUnit(),
                row.getUnitCost(),
                unitPrice,
                totalAmount(row.getQuantity(), unitPrice, row.getTotalAmount()),
                row.getDocumentNumber(),
                parseSourceType(row.getSourceType()),
                row.getSourceId(),
                row.getSourceLineId(),
                row.getCreatedById(),
                row.getCreatedByFullName(),
                row.getResponsiblePersonId(),
                row.getResponsiblePersonName(),
                row.getTakenById(),
                row.getTakenByName(),
                row.getDepartmentId(),
                row.getSupplierName(),
                row.getMovementDate(),
                row.getOccurredAt(),
                row.getNotes(),
                row.getComment() != null ? row.getComment() : row.getNotes(),
                Math.toIntExact(row.getFileCount()),
                row.getEquipmentTypeId(),
                row.getEquipmentTypeName()
        );
    }

    private static SparePartType parseSparePartType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SparePartType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static StockMovementSourceType parseSourceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StockMovementSourceType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BigDecimal unitPrice(StockMovement movement) {
        return movement.getUnitPrice() != null ? movement.getUnitPrice() : unitPriceFromLegacy(movement.getUnitCost());
    }

    private static BigDecimal unitPriceFromLegacy(Double unitCost) {
        return unitCost == null ? null : BigDecimal.valueOf(unitCost);
    }

    private static BigDecimal totalAmount(double quantity, BigDecimal unitPrice, BigDecimal storedTotal) {
        if (storedTotal != null) {
            return storedTotal;
        }
        return unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
