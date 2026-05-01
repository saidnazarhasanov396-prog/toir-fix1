package com.toir.dto.audit;

import com.toir.dto.user.UserDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.toir.enums.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponseDto(
        UUID id,
        UserDto user,
        String module,
        String entityType,
        String entityId,
        AuditAction action,
        String message,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        JsonNode previous,
        JsonNode current,
        JsonNode old,
        JsonNode newValue,
        String diffJson
) {
}
