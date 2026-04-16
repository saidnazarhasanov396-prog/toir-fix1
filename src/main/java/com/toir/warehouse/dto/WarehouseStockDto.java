package com.toir.warehouse.dto;

import com.toir.warehouse.WarehouseStock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WarehouseStockDto(
        UUID id,
        double quantity,
        double reservedQty,
        double minQty,
        Double maxQty,
        String binLocation,
        Instant updatedAt,
        SparePartRef sparePart,
        SparePartRef material,
        List<Object> reservations
) {
    public record SparePartRef(UUID id, String code, String name) {}

    public static WarehouseStockDto from(WarehouseStock s) {
        SparePartRef ref = s.getSparePartId() != null
                ? new SparePartRef(s.getSparePartId(), s.getSparePartId().toString().substring(0, 8), "")
                : null;
        return new WarehouseStockDto(
                s.getId(),
                s.getQuantity(),
                s.getReservedQty(),
                s.getMinQty(),
                s.getMaxQty(),
                s.getBinLocation(),
                s.getUpdatedAt(),
                ref,
                null,
                List.of()
        );
    }
}
