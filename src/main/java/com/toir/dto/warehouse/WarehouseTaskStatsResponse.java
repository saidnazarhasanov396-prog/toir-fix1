package com.toir.dto.warehouse;

public record WarehouseTaskStatsResponse(
        long total,
        long draft,
        long open,
        long assigned,
        long inProgress,
        long blocked,
        long done,
        long cancelled,
        long overdue
) {
}
