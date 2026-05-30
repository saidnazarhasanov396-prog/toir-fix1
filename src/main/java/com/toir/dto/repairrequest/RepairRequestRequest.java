package com.toir.dto.repairrequest;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestRequest(
        @NotBlank String number,
        @NotBlank String title,
        @NotBlank String description,
        @Schema(description = "Optional defect to link to the repair request", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID defectId,
        @NotNull UUID equipmentId,
        @NotNull UUID departmentId,
        UUID locationId,
        @NotNull UUID reporterId,
        PriorityLevel priority,
        CriticalityLevel criticality,
        RequestSource source,
        Instant targetCompletionAt
) {}
