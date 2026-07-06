package com.toir.dto.workorder;

import com.toir.enums.CloseReadinessGroupStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkOrderCloseReadinessDto(
        UUID workOrderId,
        String status,
        boolean ready,
        Instant checkedAt,
        List<WorkOrderCloseReadinessItemDto> blockers,
        List<WorkOrderCloseReadinessItemDto> warnings,
        Map<String, CloseReadinessGroupStatus> groups
) {
}
