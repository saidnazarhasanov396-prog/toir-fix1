package com.toir.repairrequest.dto;

import com.toir.common.enums.CriticalityLevel;
import com.toir.common.enums.PriorityLevel;
import com.toir.repairrequest.RequestSource;
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
