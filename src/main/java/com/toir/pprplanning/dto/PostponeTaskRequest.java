package com.toir.pprplanning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record PostponeTaskRequest(
        @NotNull LocalDateTime newDueDate,
        @NotBlank String reason
) {}
