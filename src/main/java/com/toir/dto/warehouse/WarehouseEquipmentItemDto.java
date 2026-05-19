package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.WarehouseEquipmentStatus;

import java.time.Instant;
import java.util.UUID;

public record WarehouseEquipmentItemDto(
        UUID id,
        UUID warehouseId,
        UUID equipmentId,
        WarehouseEquipmentStatus status,
        boolean active,
        Instant updatedAt
) {
    public static WarehouseEquipmentItemDto from(WarehouseEquipmentItem item) {
        return new WarehouseEquipmentItemDto(
                item.getId(),
                item.getWarehouseId(),
                item.getEquipmentId(),
                item.getStatus(),
                item.isActive(),
                item.getUpdatedAt()
        );
    }
}
