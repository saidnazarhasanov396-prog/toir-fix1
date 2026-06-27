package com.toir.dto.integration.atil;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AtilRepairRequestImportRequest(
        @NotNull UUID repairRequestId,
        @NotNull UUID vehicleId,
        UUID equipmentId,
        String number,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull UUID reporterId,
        UUID departmentId,
        PriorityLevel priority,
        CriticalityLevel criticality,
        Instant detectedAt,
        Instant targetCompletionAt,
        String sourceStatus
) {}
