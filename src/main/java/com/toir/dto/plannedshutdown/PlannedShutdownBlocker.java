package com.toir.dto.plannedshutdown;

import java.util.UUID;

public record PlannedShutdownBlocker(String code, String message, String entityType, UUID entityId,
        String entityLabel, String entityUrl, String actionHintCode) {
    public PlannedShutdownBlocker(String code, String message, String entityType, UUID entityId) {
        this(code, message, entityType, entityId, null, null, code);
    }
}
