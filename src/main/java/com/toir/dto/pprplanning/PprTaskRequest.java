package com.toir.dto.pprplanning;

import com.toir.enums.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PprTaskRequest(
        String code,
        @NotNull UUID regulationId,
        @NotNull UUID equipmentId,
        @NotBlank String title,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime dueDate,
        PriorityLevel priority,
        @PositiveOrZero double plannedLaborHours
) {}
