package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseStockBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseStockBalanceDto(
        UUID id,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal availableQty,
        BigDecimal avgCost,
        Instant updatedAt
) {
    public static WarehouseStockBalanceDto from(WarehouseStockBalance balance) {
        return new WarehouseStockBalanceDto(
                balance.getId(),
                balance.getWarehouseId(),
                balance.getSparePartId(),
                balance.getBinId(),
                balance.getLotNumber(),
                balance.getSerialNumber(),
                balance.getExpiryDate(),
                balance.getQtyOnHand(),
                balance.getQtyReserved(),
                balance.getAvailableQty(),
                balance.getAvgCost(),
                balance.getUpdatedAt()
        );
    }
}
