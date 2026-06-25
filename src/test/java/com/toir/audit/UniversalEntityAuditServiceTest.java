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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
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
    private AuditDeduplicationRegistry deduplicationRegistry;
    private UniversalEntityAuditService service;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        securityScope = mock(SecurityScope.class);
        requestContext = mock(RequestContext.class);
        deduplicationRegistry = new AuditDeduplicationRegistry();
        service = new UniversalEntityAuditService(
                auditLogService,
                new AuditSerializationService(new ObjectMapper().findAndRegisterModules()),
                new AuditRedactionService(new ObjectMapper().findAndRegisterModules()),
                securityScope,
                requestContext,
                new UniversalAuditEntityResolver(),
                deduplicationRegistry
        );
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        deduplicationRegistry.clear();
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
                orderedMap("id", entityId.toString(), "name", "Oil"),
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
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
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

    @Test
    void annotatedEntityOverridesModuleEntityTypeAndRedactsConfiguredFields() {
        UUID entityId = UUID.randomUUID();

        service.record(new UniversalEntityAuditChange(
                AnnotatedAuditEntity.class,
                entityId.toString(),
                AuditAction.UPDATE,
                orderedMap("name", "Old", "businessSecret", "old-secret"),
                orderedMap("name", "New", "businessSecret", "new-secret"),
                List.of("name", "businessSecret")
        ));

        verify(auditLogService).recordDetailed(
                eq(null),
                eq(AuditModule.WORK_ORDER),
                eq("annotated_work_orders"),
                eq(entityId.toString()),
                eq(AuditAction.UPDATE),
                eq("SYSTEM annotated_work_orders UPDATE"),
                eq(null),
                eq(null),
                eq("{\"name\":{\"old\":\"Old\",\"new\":\"New\"}}"),
                eq("{\"name\":\"Old\",\"businessSecret\":\"***REDACTED***\"}"),
                eq("{\"name\":\"New\",\"businessSecret\":\"***REDACTED***\"}"),
                eq("SYSTEM annotated_work_orders UPDATE"),
                eq("UNIVERSAL_ENTITY_LISTENER"),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @AuditedResource(
            module = AuditModule.WORK_ORDER,
            entityType = "annotated_work_orders",
            redactedFields = {"businessSecret"}
    )
    private static class AnnotatedAuditEntity {
    }

    private static Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
