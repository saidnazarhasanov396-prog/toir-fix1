package com.toir.dto.workorder;

import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderRequest(
        @NotBlank String number,
        @NotBlank String title,
        @NotNull UUID equipmentId,
        @NotNull UUID departmentId,
        UUID repairRequestId,
        UUID pprTaskId,
        UUID contractorId,
        @NotNull WorkOrderType type,
        WorkType workType,
        UUID warehouseId,
        UUID replacementEquipmentId,
        PriorityLevel priority,
        Instant startPlannedAt,
        Instant endPlannedAt,
        @NotNull UUID createdById,
        String summary
) {}
