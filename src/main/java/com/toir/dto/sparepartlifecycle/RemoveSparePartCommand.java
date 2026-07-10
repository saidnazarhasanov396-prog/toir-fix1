package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartRemovalDisposition;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RemoveSparePartCommand(
        @NotNull UUID installationId,
        Instant removedAt,
        UUID workOrderId,
        @NotNull SparePartRemovalDisposition disposition,
        String reason,
        String notes
) {
}
