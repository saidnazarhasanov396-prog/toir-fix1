package com.toir.dto.plannedshutdown;

import java.util.UUID;

public record PlannedShutdownBlocker(String code, String message, String entityType, UUID entityId) {
}
