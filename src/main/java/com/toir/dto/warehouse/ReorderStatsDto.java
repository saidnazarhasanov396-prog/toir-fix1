package com.toir.dto.warehouse;

public record ReorderStatsDto(
        long critical,
        long warning,
        long total,
        long affectedWarehouses
) {
}
