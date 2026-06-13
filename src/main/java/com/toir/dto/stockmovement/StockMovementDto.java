package com.toir.dto.stockmovement;

import com.toir.entity.StockMovement;
import com.toir.enums.SparePartType;
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
                documentNumber, createdById, createdByFullName, null, null, null, null,
                null, null, null, occurredAt, notes, notes);
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
                m.getComment() != null ? m.getComment() : m.getNotes()
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
                row.getComment() != null ? row.getComment() : row.getNotes()
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
