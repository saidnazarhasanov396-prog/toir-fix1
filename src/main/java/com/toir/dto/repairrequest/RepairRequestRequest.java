package com.toir.dto.repairrequest;

import com.toir.entity.CriticalityLevel;
import com.toir.entity.PriorityLevel;
import com.toir.entity.RequestSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestRequest(
        @NotBlank String number,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull UUID equipmentId,
        @NotNull UUID departmentId,
        UUID locationId,
        @NotNull UUID reporterId,
        PriorityLevel priority,
        CriticalityLevel criticality,
        RequestSource source,
        Instant targetCompletionAt
) {}
