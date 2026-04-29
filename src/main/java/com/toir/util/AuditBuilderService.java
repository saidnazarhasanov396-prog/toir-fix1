package com.toir.util;

import com.toir.enums.AuditAction;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditBuilderService {

    private final AuditSerializationService serializationService;
    private final AuditLogService logService;
    private final SecurityScope securityScope;

    public void log(
            String entityType,
            String resourceId,
            AuditAction action,
            String module,
            String description,
            Object oldObj,
            Object newObj
    ) {
        String oldJson = normalize(oldObj);
        String newJson = normalize(newObj);
        String diff = serializationService.diff(oldJson, newJson);
        logService.recordDetailed(
                currentUserId(),
                module,
                entityType,
                resourceId,
                action,
                description,
                null,
                null,
                diff,
                oldJson,
                newJson
        );
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        return serializationService.toJson(value);
    }

    private UUID currentUserId() {
        AuthenticatedUser user = securityScope.currentUser();
        if (user == null || user.id() == null || user.id().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(user.id());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
