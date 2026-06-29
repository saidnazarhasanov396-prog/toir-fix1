package com.toir.dto.warehouse;

import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WarehouseTaskRequest(
        @NotNull WarehouseTaskType taskType,
        WarehouseTaskStatus status,
        @NotNull UUID warehouseId,
        @NotNull WarehouseTaskSourceType sourceType,
        UUID sourceId,
        UUID assignedToId,
        Instant dueAt,
        String comment,
        @NotEmpty List<@Valid WarehouseTaskLineRequest> lines
) {
    public WarehouseTaskStatus effectiveStatus() {
        return status == null ? WarehouseTaskStatus.OPEN : status;
    }

    public WarehouseTaskPriority effectivePriority() {
        return WarehouseTaskPriority.NORMAL;
    }
}
