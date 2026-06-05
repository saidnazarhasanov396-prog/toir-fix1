package com.toir.dto.stockmovement;

import com.toir.entity.StockMovement;
import com.toir.enums.StockMovementType;
import com.toir.repository.StockMovementListRow;

import java.time.Instant;
import java.util.UUID;

public record StockMovementDto(
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
    public static StockMovementDto from(StockMovement m) {
        return new StockMovementDto(m.getId(), m.getWarehouseId(), null, m.getSparePartId(), null, m.getWorkOrderId(),
                null, null,
                m.getType(), m.getQuantity(), m.getUnitCost(), m.getDocumentNumber(),
                m.getCreatedById(), null, m.getOccurredAt(), m.getNotes());
    }

    public static StockMovementDto from(StockMovementListRow row) {
        return new StockMovementDto(
                row.getId(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getSparePartId(),
                row.getSparePartName(),
                row.getWorkOrderId(),
                row.getWorkOrderNumber(),
                row.getWorkOrderName(),
                row.getType() != null ? StockMovementType.valueOf(row.getType()) : null,
                row.getQuantity(),
                row.getUnitCost(),
                row.getDocumentNumber(),
                row.getCreatedById(),
                row.getCreatedByFullName(),
                row.getOccurredAt(),
                row.getNotes()
        );
    }
}
