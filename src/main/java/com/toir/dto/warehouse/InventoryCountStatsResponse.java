package com.toir.dto.warehouse;

public record InventoryCountStatsResponse(
        long total,
        long draft,
        long open,
        long counting,
        long review,
        long approved,
        long posted,
        long cancelled,
        long blindCount
) {
}
