package com.toir.dto.pprplanning;

import jakarta.validation.constraints.NotBlank;

public record PprPlanningVariantRequest(@NotBlank String name) {}
