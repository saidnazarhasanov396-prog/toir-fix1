package com.toir.dto.warehouse;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.WarehouseStockStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseStockLedgerDto(
        UUID id,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        StockLedgerMovementType movementType,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String referenceType,
        UUID referenceId,
        String referenceDocNo,
        Instant postedAt,
        String notes
) {
    public static WarehouseStockLedgerDto from(WarehouseStockLedger ledger) {
        return new WarehouseStockLedgerDto(
                ledger.getId(),
                ledger.getWarehouseId(),
                ledger.getSparePartId(),
                ledger.getBinId(),
                ledger.getLotNumber(),
                ledger.getSerialNumber(),
                ledger.getExpiryDate(),
                ledger.getStockStatus(),
                ledger.getMovementType(),
                ledger.getQuantity(),
                ledger.getUnitCost(),
                ledger.getTotalCost(),
                ledger.getReferenceType(),
                ledger.getReferenceId(),
                ledger.getReferenceDocNo(),
                ledger.getPostedAt(),
                ledger.getNotes()
        );
    }
}
