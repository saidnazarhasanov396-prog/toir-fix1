package com.toir.dto.plannedshutdown;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlannedShutdownAssetReplaceRequest(
        @NotNull Long version,
        @NotNull List<@Valid PlannedShutdownAssetRequest> assets
) {}
