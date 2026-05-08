package com.toir.dto.warehouse;

import com.toir.enums.WarehouseEquipmentStatus;
import jakarta.validation.constraints.NotNull;

public record WarehouseEquipmentStatusUpdateRequest(
        @NotNull WarehouseEquipmentStatus status
) {}
