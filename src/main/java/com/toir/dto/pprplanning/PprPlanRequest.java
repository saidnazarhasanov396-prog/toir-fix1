package com.toir.dto.pprplanning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PprPlanRequest(
        @NotBlank String name,
        UUID departmentId,
        @NotNull UUID createdById,
        String notes,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {}
