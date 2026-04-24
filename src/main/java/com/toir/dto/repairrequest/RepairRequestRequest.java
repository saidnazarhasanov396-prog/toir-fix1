package com.toir.dto.repairrequest;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
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
