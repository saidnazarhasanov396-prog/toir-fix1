package com.toir.dto.warehouse;

import com.toir.entity.StockMovement;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseStockMoveJournalDto(
        UUID id,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID fromBinId,
        String fromBinCode,
        UUID toBinId,
        String toBinCode,
        WarehouseStockStatus stockStatus,
        BigDecimal quantity,
        String documentNumber,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        String comment,
        Instant occurredAt,
        Instant updatedAt
) {
    public static WarehouseStockMoveJournalDto from(StockMovement movement,
                                                    String warehouseName,
                                                    String sparePartCode,
                                                    String sparePartName,
                                                    String fromBinCode,
                                                    String toBinCode) {
        return new WarehouseStockMoveJournalDto(
                movement.getId(),
                movement.getWarehouseId(),
                warehouseName,
                movement.getSparePartId(),
                sparePartCode,
                sparePartName,
                movement.getFromBinId(),
                fromBinCode,
                movement.getToBinId(),
                toBinCode,
                movement.getStockStatus(),
                BigDecimal.valueOf(movement.getQuantity()),
                movement.getDocumentNumber(),
                movement.getLotNumber(),
                movement.getSerialNumber(),
                movement.getExpiryDate(),
                movement.getComment() == null ? movement.getNotes() : movement.getComment(),
                movement.getOccurredAt(),
                movement.getUpdatedAt()
        );
    }
}
