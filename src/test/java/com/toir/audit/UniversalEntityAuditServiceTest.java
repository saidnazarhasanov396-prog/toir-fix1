package com.toir.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.Material;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.AuditLogService;
import com.toir.util.AuditSerializationService;
import com.toir.util.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniversalEntityAuditServiceTest {

    private AuditLogService auditLogService;
    private SecurityScope securityScope;
    private RequestContext requestContext;
    private UniversalEntityAuditService service;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        securityScope = mock(SecurityScope.class);
        requestContext = mock(RequestContext.class);
        service = new UniversalEntityAuditService(
                auditLogService,
                new AuditSerializationService(new ObjectMapper().findAndRegisterModules()),
                securityScope,
                requestContext,
                new UniversalAuditEntityResolver(),
                new AuditDeduplicationRegistry()
        );
    }

    @Test
    void createAuditCapturesActorRequestMetadataAndCurrentSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(securityScope.currentUser()).thenReturn(new AuthenticatedUser(
                userId.toString(), "tech", "tech@example.com", "Tech User", null, "TECHNICIAN", List.of()
        ));
        when(requestContext.getIpAddress()).thenReturn("10.0.0.7");
        when(requestContext.getUserAgent()).thenReturn("Mozilla");
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getPath()).thenReturn("/api/v1/materials");
        when(requestContext.getCorrelationId()).thenReturn("req-123");

        service.record(new UniversalEntityAuditChange(
                Material.class,
                entityId.toString(),
                AuditAction.CREATE,
                Map.of(),
                Map.of("id", entityId.toString(), "name", "Oil"),
                List.of("id", "name")
        ));

        ArgumentCaptor<String> currentSnapshot = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordDetailed(
                eq(userId),
                eq(AuditModule.MATERIAL),
                eq("materials"),
                eq(entityId.toString()),
                eq(AuditAction.CREATE),
                eq("POST /api/v1/materials materials CREATE"),
                eq("10.0.0.7"),
                eq("Mozilla"),
                eq("{\"id\":{\"old\":null,\"new\":\"" + entityId + "\"},\"name\":{\"old\":null,\"new\":\"Oil\"}}"),
                eq(null),
                currentSnapshot.capture(),
                eq("POST /api/v1/materials materials CREATE"),
                eq("UNIVERSAL_ENTITY_LISTENER"),
                eq("POST"),
                eq("/api/v1/materials"),
                eq("req-123")
        );
        assertThat(currentSnapshot.getValue()).contains("\"name\":\"Oil\"");
    }

    @Test
    void duplicateEntityActionInSameTransactionIsSkipped() {
        UUID entityId = UUID.randomUUID();
        UniversalEntityAuditChange change = new UniversalEntityAuditChange(
                Material.class,
                entityId.toString(),
                AuditAction.UPDATE,
                Map.of("name", "Oil"),
                Map.of("name", "Synthetic oil"),
                List.of("name")
        );

        service.record(change);
        service.record(change);

        verify(auditLogService).recordDetailed(
                eq(null),
                eq(AuditModule.MATERIAL),
                eq("materials"),
                eq(entityId.toString()),
                eq(AuditAction.UPDATE),
                eq("SYSTEM materials UPDATE"),
                eq(null),
                eq(null),
                eq("{\"name\":{\"old\":\"Oil\",\"new\":\"Synthetic oil\"}}"),
                eq("{\"name\":\"Oil\"}"),
                eq("{\"name\":\"Synthetic oil\"}"),
                eq("SYSTEM materials UPDATE"),
                eq("UNIVERSAL_ENTITY_LISTENER"),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void auditLogEntityItselfIsNotRecorded() {
        service.record(new UniversalEntityAuditChange(
                com.toir.entity.AuditLog.class,
                UUID.randomUUID().toString(),
                AuditAction.CREATE,
                Map.of(),
                Map.of("message", "created"),
                List.of("message")
        ));

        verify(auditLogService, never()).recordDetailed(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
