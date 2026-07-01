package com.toir.dto.warehouse;

import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WarehouseTaskDto(
        UUID id,
        String taskNumber,
        WarehouseTaskType taskType,
        WarehouseTaskStatus status,
        WarehouseTaskPriority priority,
        UUID warehouseId,
        WarehouseTaskSourceType sourceType,
        UUID sourceId,
        UUID assignedToId,
        Instant dueAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String comment,
        List<WarehouseTaskLineDto> lines,
        Instant createdAt,
        Instant updatedAt,
        String warehouseName,
        String assignedToName,
        String sourceNumber
) {}
