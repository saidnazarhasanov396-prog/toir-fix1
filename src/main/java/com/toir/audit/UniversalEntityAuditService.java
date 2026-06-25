package com.toir.audit;

import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.AuditLogService;
import com.toir.util.AuditSerializationService;
import com.toir.util.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UniversalEntityAuditService {

    public static final String SOURCE = "UNIVERSAL_ENTITY_LISTENER";

    private final AuditLogService auditLogService;
    private final AuditSerializationService serializationService;
    private final SecurityScope securityScope;
    private final RequestContext requestContext;
    private final UniversalAuditEntityResolver entityResolver;
    private final AuditDeduplicationRegistry deduplicationRegistry;

    public void record(UniversalEntityAuditChange change) {
        if (change == null || !entityResolver.isAuditable(change.entityClass())) {
            return;
        }

        String entityType = entityResolver.entityType(change.entityClass());
        if (!deduplicationRegistry.markIfFirst(entityType, change.entityId(), change.action())) {
            return;
        }

        String previous = change.previousSnapshot() == null || change.previousSnapshot().isEmpty()
                ? null
                : serializationService.toJson(change.previousSnapshot());
        String current = change.currentSnapshot() == null || change.currentSnapshot().isEmpty()
                ? null
                : serializationService.toJson(change.currentSnapshot());
        String diff = serializationService.diff(previous, current);
        String reason = defaultReason(entityType, change.action().name());

        auditLogService.recordDetailed(
                currentUserId(),
                entityResolver.module(change.entityClass()),
                entityType,
                change.entityId(),
                change.action(),
                reason,
                requestContext.getIpAddress(),
                requestContext.getUserAgent(),
                diff,
                previous,
                current,
                reason,
                SOURCE,
                requestContext.getMethod(),
                requestContext.getPath(),
                requestContext.getCorrelationId()
        );
    }

    private String defaultReason(String entityType, String action) {
        String method = requestContext.getMethod();
        String path = requestContext.getPath();
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            return "SYSTEM " + entityType + " " + action;
        }
        return method + " " + path + " " + entityType + " " + action;
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
