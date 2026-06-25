package com.toir.audit;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;

import java.util.UUID;

public record AuditLogWriteCommand(
        UUID userId,
        AuditModule module,
        String entityType,
        String entityId,
        AuditAction action,
        String message,
        String ipAddress,
        String userAgent,
        String diffJson,
        String previousSnapshot,
        String currentSnapshot,
        String reason,
        String source,
        String requestMethod,
        String requestPath,
        String correlationId
) {
}
