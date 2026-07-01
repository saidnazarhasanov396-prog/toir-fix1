package com.toir.dto.warehouse;

import com.toir.entity.StockMovement;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseQualityTransferHistoryDto(
        UUID id,
        UUID operationId,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID binId,
        String binCode,
        WarehouseStockStatus toStatus,
        BigDecimal quantity,
        String reason,
        String documentNumber,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        Instant occurredAt,
        Instant updatedAt
) {
    public static WarehouseQualityTransferHistoryDto from(StockMovement movement,
                                                          String warehouseName,
                                                          String sparePartCode,
                                                          String sparePartName,
                                                          String binCode) {
        return new WarehouseQualityTransferHistoryDto(
                movement.getId(),
                movement.getSourceId(),
                movement.getWarehouseId(),
                warehouseName,
                movement.getSparePartId(),
                sparePartCode,
                sparePartName,
                movement.getBinId(),
                binCode,
                movement.getStockStatus(),
                BigDecimal.valueOf(movement.getQuantity()),
                movement.getNotes(),
                movement.getDocumentNumber(),
                movement.getLotNumber(),
                movement.getSerialNumber(),
                movement.getExpiryDate(),
                movement.getOccurredAt(),
                movement.getUpdatedAt()
        );
    }
}
