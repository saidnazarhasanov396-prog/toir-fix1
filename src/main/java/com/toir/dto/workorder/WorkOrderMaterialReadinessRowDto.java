package com.toir.dto.workorder;

import com.toir.enums.MaterialReadinessStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderMaterialReadinessRowDto(
        UUID requirementId,
        UUID sparePartId,
        String sparePartName,
        String unit,
        double requiredQty,
        double reservedQty,
        double issuedQty,
        double returnedQty,
        double shortageQty,
        MaterialReadinessStatus readinessStatus,
        boolean blocking,
        LocalDate expectedDate,
        String sourceSystem,
        Instant lastSyncedAt,
        String notes
) {
}
