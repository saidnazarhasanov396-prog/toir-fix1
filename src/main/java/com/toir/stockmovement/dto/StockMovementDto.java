package com.toir.stockmovement.dto;

import com.toir.stockmovement.StockMovement;
import com.toir.stockmovement.StockMovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementDto(
        UUID id,
        UUID warehouseId,
        UUID sparePartId,
        UUID workOrderId,
        StockMovementType type,
        double quantity,
        Double unitCost,
        String documentNumber,
        UUID createdById,
        Instant occurredAt,
        String notes
) {
    public static StockMovementDto from(StockMovement m) {
        return new StockMovementDto(m.getId(), m.getWarehouseId(), m.getSparePartId(), m.getWorkOrderId(),
                m.getType(), m.getQuantity(), m.getUnitCost(), m.getDocumentNumber(),
                m.getCreatedById(), m.getOccurredAt(), m.getNotes());
    }
}
