package com.toir.dto.sparepartlifecycle;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InstallSparePartCommand(
        UUID equipmentNodeId,
        String slotCode,
        String positionLabel,
        @NotNull UUID sparePartId,
        @NotNull BigDecimal quantity,
        String serialNumber,
        String lotNumber,
        Instant installedAt,
        UUID workOrderId,
        UUID sourceMaterialUsageId,
        String externalSourceReason,
        String notes
) {
}
