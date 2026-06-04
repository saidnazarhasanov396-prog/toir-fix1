package com.toir.dto.sparepartforecast;

import com.toir.enums.NotificationSeverity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartForecastItemDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID warehouseId,
        String warehouseName,
        double requiredQty,
        double availableQty,
        double reservedQty,
        double shortageQty,
        String unit,
        NotificationSeverity severity,
        Instant firstDueAt,
        int sourceCount,
        List<SparePartForecastSourceDto> sources
) {
}
