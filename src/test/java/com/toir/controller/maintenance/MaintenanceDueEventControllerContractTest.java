package com.toir.controller.maintenance;

import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceDueEventService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceDueEventControllerContractTest {

    @Mock
    MaintenanceDueEventService service;

    @Mock
    MaintenanceAutomationService automationService;

    @Mock
    ScopeAccessService scopeAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MaintenanceDueEventController(service, automationService, scopeAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getReturnsExactAccessibleEventUsingExistingLookupAndMapper() throws Exception {
        UUID eventId = UUID.fromString("0a70ebd3-f4c1-4e27-a7ed-e9968ef31fe7");
        MaintenanceDueEvent event = mock(MaintenanceDueEvent.class);
        MaintenanceDueEventDto dto = dueEventDto(eventId);
        when(service.getOrThrow(eventId)).thenReturn(event);
        when(service.toDto(event, "uz")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/maintenance-due-events/{id}", eventId)
                        .param("lang", "uz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()));

        InOrder accessPath = inOrder(service);
        accessPath.verify(service).getOrThrow(eventId);
        accessPath.verify(service).assertCanAccessEvent(event);
        accessPath.verify(service).toDto(event, "uz");
    }

    @Test
    void getReturnsNotFoundWhenEventIsMissingOrDeleted() throws Exception {
        UUID eventId = UUID.fromString("30f0893f-1274-42e3-b82b-dd28d46b53c8");
        when(service.getOrThrow(eventId))
                .thenThrow(RestException.notFound("Maintenance due event not found: " + eventId));

        mockMvc.perform(get("/api/v1/maintenance-due-events/{id}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPreservesForbiddenResponseForInaccessibleEvent() throws Exception {
        UUID eventId = UUID.fromString("2e51469d-38f7-436e-b6de-85b5a5813684");
        MaintenanceDueEvent event = mock(MaintenanceDueEvent.class);
        when(service.getOrThrow(eventId)).thenReturn(event);
        doThrow(new AccessDeniedException("Department access denied"))
                .when(service).assertCanAccessEvent(event);

        mockMvc.perform(get("/api/v1/maintenance-due-events/{id}", eventId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsesMaintenanceEventReadPermission() throws Exception {
        var method = MaintenanceDueEventController.class.getDeclaredMethod(
                "get", UUID.class, String.class, String.class);
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);

        assertThat(authorize).isNotNull();
        assertThat(authorize.value()).contains("MAINTENANCE_EVENT_READ");
    }

    private MaintenanceDueEventDto dueEventDto(UUID id) {
        return new MaintenanceDueEventDto(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                MaintenanceDueEventStatus.DETECTED,
                MaintenanceDueStatus.DUE,
                MaintenanceTriggerSource.CALENDAR_JOB,
                "cycle",
                Instant.parse("2026-07-20T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-07-20T00:00:00Z"),
                null,
                null,
                "Maintenance is due",
                null,
                null,
                null
        );
    }
}
