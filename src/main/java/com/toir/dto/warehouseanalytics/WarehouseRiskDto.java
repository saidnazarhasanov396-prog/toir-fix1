package com.toir.dto.warehouseanalytics;

import java.util.UUID;

public record WarehouseRiskDto(
        String severity,
        String title,
        String description,
        String actionType,
        UUID actionTargetId,
        String actionLabel
) {
}
