package com.toir.dto.actualcost;

import jakarta.validation.constraints.NotBlank;

public record ActualCostCorrectionRequest(
        @NotBlank String comment
) {
}
