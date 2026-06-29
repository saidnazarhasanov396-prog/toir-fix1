package com.toir.dto.warehouse;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.WarehouseStockStatus;
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
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        String qualityHoldReason,
        Instant qualityCheckedAt,
        UUID qualityCheckedById,
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
                balance.getStockStatus(),
                balance.getQualityHoldReason(),
                balance.getQualityCheckedAt(),
                balance.getQualityCheckedById(),
                balance.getQtyOnHand(),
                balance.getQtyReserved(),
                balance.getAvailableQty(),
                balance.getAvgCost(),
                balance.getUpdatedAt()
        );
    }
}
