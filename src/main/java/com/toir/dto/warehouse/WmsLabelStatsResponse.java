package com.toir.dto.warehouse;

import java.time.Instant;

public record WmsLabelStatsResponse(
        long totalPrinted,
        long binLabels,
        long sparePartLabels,
        long equipmentLabels,
        Instant lastPrintedAt
) {
}
