package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartRemovalDisposition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ReplaceSparePartCommand(
        @NotNull UUID oldInstallationId,
        @NotNull @Valid ReplacementPartCommand newPart,
        Instant replacedAt,
        UUID workOrderId,
        @NotNull SparePartRemovalDisposition oldPartDisposition,
        String reason,
        String notes
) {
}
