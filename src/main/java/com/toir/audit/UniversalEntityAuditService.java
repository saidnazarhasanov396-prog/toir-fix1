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
    private final AuditRedactionService redactionService;
    private final SecurityScope securityScope;
    private final RequestContext requestContext;
    private final UniversalAuditEntityResolver entityResolver;
    private final AuditDeduplicationRegistry deduplicationRegistry;

    public void record(UniversalEntityAuditChange change) {
        if (change == null || !entityResolver.isAuditable(change.entityClass())) {
            return;
        }

        String entityType = entityResolver.entityType(change.entityClass());
        String previousRaw = change.previousSnapshot() == null || change.previousSnapshot().isEmpty()
                ? null
                : serializationService.toJson(change.previousSnapshot());
        String currentRaw = change.currentSnapshot() == null || change.currentSnapshot().isEmpty()
                ? null
                : serializationService.toJson(change.currentSnapshot());
        String previous = redactionService.redactJson(previousRaw, entityResolver.redactedFields(change.entityClass()));
        String current = redactionService.redactJson(currentRaw, entityResolver.redactedFields(change.entityClass()));
        String diff = serializationService.diff(previous, current);
        if (!deduplicationRegistry.markIfFirst(entityType, change.entityId(), change.action(), diff)) {
            return;
        }
        String message = defaultMessage(entityType, change.action());

        auditLogService.recordDetailed(
                currentUserId(),
                entityResolver.module(change.entityClass()),
                entityType,
                change.entityId(),
                change.action(),
                message,
                requestContext.getIpAddress(),
                requestContext.getUserAgent(),
                diff,
                previous,
                current,
                null,
                SOURCE,
                requestContext.getMethod(),
                requestContext.getPath(),
                requestContext.getCorrelationId()
        );
    }

    private String defaultMessage(String entityType, com.toir.enums.AuditAction action) {
        return switch (action) {
            case CREATE -> "Created " + humanize(entityType);
            case UPDATE -> "Updated " + humanize(entityType);
            case DELETE -> "Deleted " + humanize(entityType);
            case LOGIN -> "User login";
            case APPROVE -> "Approved " + humanize(entityType);
            case CLOSE -> "Closed " + humanize(entityType);
            case CANCEL -> "Cancelled " + humanize(entityType);
            case EXPORT -> "Exported " + humanize(entityType);
        };
    }

    private String humanize(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return "entity";
        }
        return entityType.replace('_', ' ');
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
