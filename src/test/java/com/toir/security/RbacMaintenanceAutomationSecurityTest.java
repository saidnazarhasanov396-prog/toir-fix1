package com.toir.security;

import com.toir.controller.equipment.EquipmentMaintenanceAutomationController;
import com.toir.controller.maintenance.MaintenanceDueEventController;
import com.toir.controller.maintenance.MaintenanceRegulationController;
import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationImpactDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationPreviewDto;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.PeriodicityUnit;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.service.maintanance.MaintenanceImpactService;
import com.toir.service.maintanance.MaintenanceRegulationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MaintenanceRegulationController.class,
        MaintenanceDueEventController.class,
        EquipmentMaintenanceAutomationController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacMaintenanceAutomationSecurityTest.SecurityBeans.class
})
class RbacMaintenanceAutomationSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    MaintenanceRegulationService maintenanceRegulationService;

    @MockBean
    MaintenanceImpactService maintenanceImpactService;

    @MockBean
    MaintenanceDueEventService maintenanceDueEventService;

    @MockBean
    MaintenanceAutomationService maintenanceAutomationService;

    @MockBean
    ScopeAccessService scopeAccessService;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadRegulationsOrDueEvents() throws Exception {
        mockMvc.perform(get("/api/v1/maintenance-regulations?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/maintenance-due-events?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_READ)
    void regulationReadCanListPreviewAndImpact() throws Exception {
        UUID regulationId = UUID.randomUUID();
        when(maintenanceRegulationService.search(anyInt(), anyInt(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Page.empty());
        when(maintenanceImpactService.preview(any())).thenReturn(preview());
        when(maintenanceImpactService.impact(regulationId)).thenReturn(impact(regulationId));

        mockMvc.perform(get("/api/v1/maintenance-regulations?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/maintenance-regulations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regulationPayload(false)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/maintenance-regulations/{id}/impact", regulationId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_CREATE)
    void regulationCreateCanCreatePlainRegulation() throws Exception {
        when(maintenanceRegulationService.create(any())).thenReturn(regulationDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/maintenance-regulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regulationPayload(false)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_UPDATE)
    void regulationUpdateCanUpdatePlainRegulation() throws Exception {
        UUID regulationId = UUID.randomUUID();
        when(maintenanceRegulationService.update(eq(regulationId), any())).thenReturn(regulationDto(regulationId));

        mockMvc.perform(put("/api/v1/maintenance-regulations/{id}", regulationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regulationPayload(false)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_DELETE)
    void regulationDeleteCanDeleteRegulation() throws Exception {
        mockMvc.perform(delete("/api/v1/maintenance-regulations/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_APPROVE)
    void pprTaskApproveCannotApproveMaintenanceDueEvent() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-due-events/{id}/approve", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_EVENT_APPROVE)
    void maintenanceEventApproveCanApproveDueEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(maintenanceAutomationService.approveDueEvent(eq(eventId), any())).thenReturn(dueEventDto(eventId));

        mockMvc.perform(post("/api/v1/maintenance-due-events/{id}/approve", eventId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_CANCEL)
    void pprTaskCancelCannotCancelMaintenanceDueEvent() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-due-events/{id}/cancel", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_EVENT_CANCEL)
    void maintenanceEventCancelCanCancelDueEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(maintenanceDueEventService.cancel(eq(eventId), anyString())).thenReturn(new com.toir.entity.maintenance.MaintenanceDueEvent());
        when(maintenanceDueEventService.toDto(any())).thenReturn(dueEventDto(eventId));

        mockMvc.perform(post("/api/v1/maintenance-due-events/{id}/cancel", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_UPDATE)
    void equipmentUpdateCannotRunMaintenanceRecalculation() throws Exception {
        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/maintenance/recalculate", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_AUTOMATION_RUN)
    void maintenanceAutomationRunCanRunRecalculation() throws Exception {
        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/maintenance/recalculate", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    private String regulationPayload(boolean automationConfig) {
        return """
                {
                  "name": "Monthly service",
                  "equipmentTypeId": "%s",
                  "maintenanceKind": "PREVENTIVE",
                  "normativeLaborHours": 1.0,
                  "active": true,
                  "periodicityUnit": "MONTH",
                  "periodicityValue": 1,
                  "requiresShutdown": false%s
                }
                """.formatted(UUID.randomUUID(), automationConfig ? ",\"automationAction\":\"CREATE_WORK_ORDER\"" : "");
    }

    private MaintenanceRegulationDto regulationDto(UUID id) {
        return new MaintenanceRegulationDto(
                id,
                "MR-2026-0001",
                "Monthly service",
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                MaintenanceKind.PREVENTIVE,
                1.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                null,
                false,
                null,
                null,
                MaintenanceTriggerPolicy.ANY,
                MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION,
                AutomationAction.TRACK_ONLY,
                ApprovalResultAction.CREATE_TASK,
                DuplicatePolicy.ONE_ITEM_PER_CYCLE,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                List.of()
        );
    }

    private MaintenanceRegulationPreviewDto preview() {
        return new MaintenanceRegulationPreviewDto(0, 0, 0, 0, 0, "TRACK_ONLY", DuplicatePolicy.ONE_ITEM_PER_CYCLE, List.of());
    }

    private MaintenanceRegulationImpactDto impact(UUID regulationId) {
        return new MaintenanceRegulationImpactDto(regulationId, 0, 0, 0, 0, 0, "TRACK_ONLY", DuplicatePolicy.ONE_ITEM_PER_CYCLE, List.of());
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
                Instant.parse("2026-06-03T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-03T00:00:00Z"),
                null,
                null,
                null,
                null,
                null
        );
    }
}
