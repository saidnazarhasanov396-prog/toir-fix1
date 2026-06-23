package com.toir.dto.notification;

import java.util.List;
import java.util.UUID;

public record BulkNotificationReadResponse(
        int processed,
        int updated,
        int failed,
        List<UUID> updatedIds,
        List<Failure> failures
) {
    public record Failure(UUID id, String message) {
    }
}
