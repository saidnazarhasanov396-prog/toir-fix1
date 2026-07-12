package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PlannedShutdownIsolationPointRequest(@NotNull Long version, @NotNull UUID equipmentId,
        UUID locationId, @NotBlank @Size(max = 255) String isolationMethod,
        @NotBlank @Size(max = 255) String lockTagIdentifier, @NotNull UUID responsibleEmployeeId,
        UUID permitId, @NotNull @PositiveOrZero Integer orderNumber) {
}
