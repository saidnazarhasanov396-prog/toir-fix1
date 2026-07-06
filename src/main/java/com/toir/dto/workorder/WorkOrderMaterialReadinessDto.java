package com.toir.dto.workorder;

import com.toir.enums.MaterialReadinessStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderMaterialReadinessDto(
        UUID workOrderId,
        UUID equipmentId,
        MaterialReadinessStatus overallStatus,
        boolean blocking,
        Instant checkedAt,
        List<WorkOrderMaterialReadinessRowDto> rows
) {
}
