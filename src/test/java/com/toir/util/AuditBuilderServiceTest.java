package com.toir.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.enums.AuditAction;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.AuditLogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditBuilderServiceTest {

    @Test
    void logSerializesSnapshotsAndPersistsDiff() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        SecurityScope securityScope = mock(SecurityScope.class);
        UUID userId = UUID.randomUUID();
        when(securityScope.currentUser()).thenReturn(new AuthenticatedUser(
                userId.toString(),
                "admin",
                "admin@example.com",
                "Admin",
                null,
                "SYSTEM_ADMIN",
                List.of()
        ));
        AuditSerializationService serializationService = new AuditSerializationService(new ObjectMapper().findAndRegisterModules());
        AuditBuilderService service = new AuditBuilderService(serializationService, auditLogService, securityScope);

        service.log(
                "locations",
                "resource-1",
                AuditAction.UPDATE,
                "LOCATION",
                "Location updated",
                "{\"name\":\"Old\"}",
                "{\"name\":\"New\"}"
        );

        verify(auditLogService).recordDetailed(
                eq(userId),
                eq("LOCATION"),
                eq("locations"),
                eq("resource-1"),
                eq(AuditAction.UPDATE),
                eq("Location updated"),
                isNull(),
                isNull(),
                contains("\"name\""),
                eq("{\"name\":\"Old\"}"),
                eq("{\"name\":\"New\"}")
        );
    }
}
