package com.toir.dto.plannedshutdown;

import com.toir.enums.PlannedShutdownReadinessSeverity;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownReadinessItemRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 128) String readinessKey,
        @Size(max = 64) String sourceType,
        UUID sourceId,
        @NotBlank @Size(max = 500) String title,
        @NotNull PlannedShutdownReadinessSeverity severity,
        UUID responsibleEmployeeId,
        Instant dueAt,
        String evidence,
        String comment,
        @NotNull @PositiveOrZero Integer orderNumber) {
}
