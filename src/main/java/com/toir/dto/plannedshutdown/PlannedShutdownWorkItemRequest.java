package com.toir.dto.plannedshutdown;

import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PriorityLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PlannedShutdownWorkItemRequest(
        @NotNull Long version,
        @NotNull PlannedShutdownWorkItemSourceType sourceType,
        UUID sourceId,
        @NotNull UUID equipmentId,
        @NotBlank @Size(max = 500) String title,
        @NotNull PriorityLevel priority,
        @NotNull Boolean requiresShutdown,
        @NotNull Boolean requiresIsolation,
        @Positive Integer plannedDurationMinutes,
        @Size(max = 32) String criticality,
        @NotNull @PositiveOrZero Integer orderNumber) {
}
