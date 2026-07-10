package com.toir.dto.sparepartlifecycle;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ReplacementPartCommand(
        @NotNull UUID sparePartId,
        @NotNull BigDecimal quantity,
        String serialNumber,
        String lotNumber,
        UUID sourceMaterialUsageId,
        String externalSourceReason
) {
}
