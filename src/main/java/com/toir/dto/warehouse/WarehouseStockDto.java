package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseStock;

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
        SparePartRef spare = s.getSparePart() != null
                ? new SparePartRef(s.getSparePart().getId(), s.getSparePart().getCode(), s.getSparePart().getName())
                : null;
        return new WarehouseStockDto(
                s.getId(),
                s.getQuantity(),
                s.getReservedQty(),
                s.getMinQty(),
                s.getMaxQty(),
                s.getBinLocation(),
                s.getUpdatedAt(),
                spare,
                null,
                List.of()
        );
    }
}
