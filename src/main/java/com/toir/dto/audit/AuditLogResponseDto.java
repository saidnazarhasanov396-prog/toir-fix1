package com.toir.dto.audit;

import com.toir.enums.AuditAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponseDto(
        UUID id,
        UUID userId,
        String module,
        String entityType,
        String entityId,
        AuditAction action,
        String message,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Map<String, Object> previous,
        Map<String, Object> current,
        Map<String, Object> old,
        Map<String, Object> newValue,
        String diffJson
) {
}
