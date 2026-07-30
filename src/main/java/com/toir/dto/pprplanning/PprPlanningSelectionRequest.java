package com.toir.dto.pprplanning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PprPlanningSelectionRequest(
        @NotNull UUID variantId,
        @Positive long revision,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String contentHash
) {}
