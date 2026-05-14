package com.toir.dto.equipment;

import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EquipmentPlacementRequest(
        @NotNull PlacementTargetType targetType,
        UUID warehouseId,
        UUID departmentId,
        WarehouseEquipmentStatus warehouseStatus
) {}
