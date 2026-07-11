package com.toir.security;

import com.toir.controller.PlannedShutdownController;
import com.toir.controller.repair.RepairCampaignController;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.enums.PlanStatus;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.PlannedShutdownService;
import com.toir.service.repair.RepairCampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PlannedShutdownController.class, RepairCampaignController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacToirBusinessFlowSecurityTest.SecurityBeans.class
})
class RbacToirBusinessFlowSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    PlannedShutdownService plannedShutdownService;

    @MockBean
    RepairCampaignService repairCampaignService;

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
    void approvalFallbackUsesBusinessFlowSpecificPermissions() {
        assertThat(ApprovalDomainPermissions.approvePermissionFor(ApprovalTargetType.PLANNED_SHUTDOWN))
                .contains("PLANNED_SHUTDOWN_APPROVE");
        assertThat(ApprovalDomainPermissions.approvePermissionFor(ApprovalTargetType.REPAIR_CAMPAIGN))
                .contains("REPAIR_CAMPAIGN_APPROVE");
        assertThat(ApprovalSecurityExpressions.CAN_CREATE)
                .contains("PLANNED_SHUTDOWN_APPROVE", "REPAIR_CAMPAIGN_APPROVE");
        assertThat(ApprovalSecurityExpressions.CAN_APPROVE)
                .contains("PLANNED_SHUTDOWN_APPROVE", "REPAIR_CAMPAIGN_APPROVE");
        assertThat(ApprovalSecurityExpressions.CAN_REJECT)
                .contains("PLANNED_SHUTDOWN_APPROVE", "REPAIR_CAMPAIGN_APPROVE");
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedUserCannotReadOrCreatePlannedShutdown() throws Exception {
        mockMvc.perform(get("/api/v1/planned-shutdowns"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/planned-shutdowns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plannedShutdownPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_READ)
    void plannedShutdownReaderCanRead() throws Exception {
        when(plannedShutdownService.findAllFiltered(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/planned-shutdowns"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_CREATE)
    void plannedShutdownCreatorCanCreate() throws Exception {
        when(plannedShutdownService.create(any())).thenReturn(plannedShutdownDto());

        mockMvc.perform(post("/api/v1/planned-shutdowns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plannedShutdownPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedUserCannotCreateUpdateGenerateOrCancelCampaign() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/repair-campaigns/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/generate-work-orders", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cancelled\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedUserCannotStartOrCloseCampaign() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/close", id))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_CAMPAIGN_START)
    void campaignStarterCanStartCampaign() throws Exception {
        when(repairCampaignService.start(any())).thenReturn(campaignDto());

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_CAMPAIGN_CREATE)
    void campaignCreatorCanCreateCampaign() throws Exception {
        when(repairCampaignService.create(any())).thenReturn(campaignDto());

        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_CAMPAIGN_UPDATE)
    void campaignUpdaterCanUpdateCampaign() throws Exception {
        when(repairCampaignService.update(any(), any())).thenReturn(campaignDto());

        mockMvc.perform(put("/api/v1/repair-campaigns/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS)
    void campaignWorkOrderGeneratorCanGenerate() throws Exception {
        when(repairCampaignService.generateWorkOrders(any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/generate-work-orders", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_CAMPAIGN_CANCEL)
    void campaignCancellerCanCancel() throws Exception {
        when(repairCampaignService.cancel(any(), any())).thenReturn(campaignDto());

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/cancel", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cancelled\"}"))
                .andExpect(status().isOk());
    }

    private static String plannedShutdownPayload() {
        return """
                {"name":"Annual shutdown","departmentId":"%s","startAt":"2026-08-01T00:00:00Z","endAt":"2026-08-02T00:00:00Z","reason":"Maintenance"}
                """.formatted(UUID.randomUUID());
    }

    private static PlannedShutdownDto plannedShutdownDto() {
        return new PlannedShutdownDto(UUID.randomUUID(), "Annual shutdown", UUID.randomUUID(),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", PlanStatus.DRAFT);
    }

    private static String campaignPayload() {
        return """
                {"name":"Annual repair","departmentId":"%s","startDate":"2026-08-01","endDate":"2026-08-10","totalBudget":1000}
                """.formatted(UUID.randomUUID());
    }

    private static RepairCampaignDto campaignDto() {
        return new RepairCampaignDto(UUID.randomUUID(), "RC-1", "Annual repair", UUID.randomUUID(),
                "Maintenance", RepairCampaignStatus.DRAFT, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10), 1000, 0, 1000, null, null, List.of());
    }
}
