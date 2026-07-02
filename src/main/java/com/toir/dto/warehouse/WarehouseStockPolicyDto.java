package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseStockPolicy;

import java.time.Instant;
import java.util.UUID;

public record WarehouseStockPolicyDto(
        UUID id,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartName,
        String sparePartCode,
        double minQty,
        Double maxQty,
        Double reorderPoint,
        Double reorderQty,
        Double avgDailyUsage,
        Instant updatedAt
) {
    public static WarehouseStockPolicyDto from(WarehouseStockPolicy policy,
                                               String warehouseName,
                                               String sparePartName,
                                               String sparePartCode) {
        return new WarehouseStockPolicyDto(
                policy.getId(),
                policy.getWarehouseId(),
                warehouseName,
                policy.getSparePartId(),
                sparePartName,
                sparePartCode,
                policy.getMinQty(),
                policy.getMaxQty(),
                policy.getReorderPoint(),
                policy.getReorderQty(),
                policy.getAvgDailyUsage(),
                policy.getUpdatedAt()
        );
    }
}
