package com.toir.dto.actualcost;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ActualCostAllocationRequest(
        @NotNull UUID budgetLineId,
        @NotBlank String comment
) {
}
