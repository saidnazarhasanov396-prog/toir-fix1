package com.toir.dto.warehouse;

import com.toir.enums.WarehouseEquipmentStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WarehouseEquipmentAssignRequest(
        @NotNull UUID equipmentId,
        WarehouseEquipmentStatus status
) {}
