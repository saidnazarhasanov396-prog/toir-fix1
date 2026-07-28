package com.toir.service;

import java.util.Map;

public record NotificationNavigation(
        String eventType,
        String entityType,
        String entityId,
        String actionUrl,
        Map<String, Object> metadata
) {
}
