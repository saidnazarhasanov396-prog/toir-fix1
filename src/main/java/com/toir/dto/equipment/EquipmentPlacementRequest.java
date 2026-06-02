package com.toir.dto.equipment;

import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;

import java.util.UUID;

public record EquipmentPlacementRequest(
        PlacementTargetType targetType,
        UUID warehouseId,
        UUID departmentId,
        WarehouseEquipmentStatus warehouseStatus,
        EquipmentLocationRequest targetLocation,
        String note
) {
        public EquipmentPlacementRequest(
                PlacementTargetType targetType,
                UUID warehouseId,
                UUID departmentId,
                WarehouseEquipmentStatus warehouseStatus
        ) {
                this(targetType, warehouseId, departmentId, warehouseStatus, null, null);
        }
}
