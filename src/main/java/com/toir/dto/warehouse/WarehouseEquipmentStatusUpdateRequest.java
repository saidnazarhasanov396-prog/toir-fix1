package com.toir.dto.warehouse;

import com.toir.enums.WarehouseEquipmentStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WarehouseEquipmentStatusUpdateRequest(
        @NotNull WarehouseEquipmentStatus status,
        UUID departmentId
) {}
