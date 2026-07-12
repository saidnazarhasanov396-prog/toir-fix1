package com.toir.dto.plannedshutdown;

import com.toir.enums.PlannedShutdownAssetDisposition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlannedShutdownAssetRequest(
        @NotNull UUID equipmentId,
        @NotNull PlannedShutdownAssetDisposition disposition,
        String inclusionReason,
        @Min(0) int orderNumber
) {}
