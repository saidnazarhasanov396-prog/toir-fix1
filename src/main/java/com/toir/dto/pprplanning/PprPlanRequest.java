package com.toir.dto.pprplanning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PprPlanRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull @Min(2000) Integer year,
        @NotNull @Min(1) @Max(12) Integer month,
        UUID departmentId,
        @NotNull UUID createdById,
        String notes
) {}
