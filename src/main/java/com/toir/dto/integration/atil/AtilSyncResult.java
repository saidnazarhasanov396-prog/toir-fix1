package com.toir.dto.integration.atil;

import java.util.List;
import java.util.UUID;

public record AtilSyncResult(
        int created,
        int updated,
        int failed,
        int received,
        List<ItemResult> items
) {
    public AtilSyncResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static AtilSyncResult success(int created, int updated, int received, List<ItemResult> items) {
        return new AtilSyncResult(created, updated, 0, received, items);
    }

    public record ItemResult(
            String sourceEntityType,
            String sourceEntityId,
            UUID targetEntityId,
            String action,
            String status,
            String message
    ) {}
}
