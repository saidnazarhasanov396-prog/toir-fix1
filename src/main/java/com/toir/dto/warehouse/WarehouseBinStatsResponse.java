package com.toir.dto.warehouse;

public record WarehouseBinStatsResponse(
        long total,
        long active,
        long inactive,
        long blocked,
        long frozen,
        long storage,
        long receiving,
        long picking,
        long quarantine
) {
}
