package com.toir.util;

import com.toir.audit.AuditDeduplicationRegistry;
import com.toir.audit.AuditRedactionService;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
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
    private final RequestContext requestContext;
    private final AuditDeduplicationRegistry deduplicationRegistry;
    private final AuditRedactionService redactionService;

    public <T> void log(
            String entityType,
            String resourceId,
            AuditAction action,
            AuditModule module,
            String description,
            T oldObj,
            T newObj
    ) {
        String oldJson = redactionService.redactJson(normalize(oldObj), java.util.Set.of());
        String newJson = redactionService.redactJson(normalize(newObj), java.util.Set.of());
        String diff = serializationService.diff(oldJson, newJson);
        if (!deduplicationRegistry.markIfFirst(entityType, resourceId, action, diff)) {
            return;
        }
        logService.recordDetailed(
                currentUserId(),
                module,
                entityType,
                resourceId,
                action,
                description,
                requestContext.getIpAddress(),
                requestContext.getUserAgent(),
                diff,
                oldJson,
                newJson,
                description,
                "AUDIT_BUILDER_SERVICE",
                requestContext.getMethod(),
                requestContext.getPath(),
                requestContext.getCorrelationId()
        );
    }

    private <T> String normalize(T value) {
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
