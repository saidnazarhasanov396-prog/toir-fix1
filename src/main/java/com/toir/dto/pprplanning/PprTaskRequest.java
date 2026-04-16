package com.toir.dto.pprplanning;

import com.toir.entity.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.util.UUID;

public record PprTaskRequest(
        @NotBlank String code,
        @NotNull UUID regulationId,
        @NotNull UUID equipmentId,
        @NotBlank String title,
        @NotNull LocalDateTime scheduledStart,
        @NotNull LocalDateTime scheduledEnd,
        @NotNull LocalDateTime dueDate,
        PriorityLevel priority,
        @PositiveOrZero double plannedLaborHours
) {}
