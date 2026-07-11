package com.toir.security;

import com.toir.controller.PlannedShutdownController;
import com.toir.controller.repair.RepairCampaignController;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.dto.plannedshutdown.PlannedShutdownCreateRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownDetailResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownUpdateRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetReplaceRequest;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.enums.PlanStatus;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.PlannedShutdownService;
import com.toir.service.repair.RepairCampaignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    @ParameterizedTest
    @MethodSource("operationAnnotations")
    void controllerOperationsDeclareExactBusinessFlowPermission(
            Class<?> controller,
            String methodName,
            Class<?>[] parameterTypes,
            String permission
    ) throws Exception {
        PreAuthorize annotation = controller.getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .contains("hasAuthority('SYSTEM_ADMIN')", "hasAuthority('*')", "hasAuthority('" + permission + "')");
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
        when(plannedShutdownService.create(any())).thenReturn(plannedShutdownDetail());

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
        when(repairCampaignService.generateWorkOrders(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/generate-work-orders", UUID.randomUUID())
                        .header("Idempotency-Key", "security-test-generation")
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
                {"name":"Annual shutdown","shutdownType":"PLANNED","departmentId":"%s","responsibleEmployeeId":"%s","startAt":"2026-08-01T00:00:00Z","endAt":"2026-08-02T00:00:00Z","reason":"Maintenance"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private static PlannedShutdownDto plannedShutdownDto() {
        return new PlannedShutdownDto(UUID.randomUUID(), "Annual shutdown", UUID.randomUUID(),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "Maintenance", PlanStatus.DRAFT);
    }

    private static PlannedShutdownDetailResponse plannedShutdownDetail() {
        return new PlannedShutdownDetailResponse(UUID.randomUUID(), 0L, "PS-1", "Annual shutdown", "PLANNED",
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"), "Maintenance", null, null, null, null,
                PlannedShutdownStatus.DRAFT, 0L, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, 0L, null, null, List.of());
    }

    private static String campaignPayload() {
        return """
                {"name":"Annual repair","departmentId":"%s","startDate":"2026-08-01","endDate":"2026-08-10","totalBudget":"1000","currencyCode":"UZS"}
                """.formatted(UUID.randomUUID());
    }

    private static RepairCampaignDto campaignDto() {
        return new RepairCampaignDto(UUID.randomUUID(), "RC-1", "Annual repair", UUID.randomUUID(),
                "Maintenance", RepairCampaignStatus.DRAFT, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("1000"), null, null, List.of());
    }

    private static Stream<Arguments> operationAnnotations() {
        return Stream.of(
                Arguments.of(PlannedShutdownController.class, "list",
                        new Class<?>[]{UUID.class, PlanStatus.class, String.class, int.class, int.class, String.class, String.class},
                        PermissionConstants.PLANNED_SHUTDOWN_READ),
                Arguments.of(PlannedShutdownController.class, "create",
                        new Class<?>[]{PlannedShutdownCreateRequest.class}, PermissionConstants.PLANNED_SHUTDOWN_CREATE),
                Arguments.of(PlannedShutdownController.class, "get",
                        new Class<?>[]{UUID.class}, PermissionConstants.PLANNED_SHUTDOWN_READ),
                Arguments.of(PlannedShutdownController.class, "update",
                        new Class<?>[]{UUID.class, PlannedShutdownUpdateRequest.class}, PermissionConstants.PLANNED_SHUTDOWN_UPDATE),
                Arguments.of(PlannedShutdownController.class, "getAssets",
                        new Class<?>[]{UUID.class}, PermissionConstants.PLANNED_SHUTDOWN_READ),
                Arguments.of(PlannedShutdownController.class, "replaceAssets",
                        new Class<?>[]{UUID.class, PlannedShutdownAssetReplaceRequest.class}, PermissionConstants.PLANNED_SHUTDOWN_UPDATE),
                Arguments.of(RepairCampaignController.class, "get",
                        new Class<?>[]{UUID.class}, PermissionConstants.REPAIR_CAMPAIGN_READ),
                Arguments.of(RepairCampaignController.class, "create",
                        new Class<?>[]{com.toir.dto.repaircampaign.RepairCampaignRequest.class},
                        PermissionConstants.REPAIR_CAMPAIGN_CREATE),
                Arguments.of(RepairCampaignController.class, "update",
                        new Class<?>[]{UUID.class, com.toir.dto.repaircampaign.RepairCampaignRequest.class},
                        PermissionConstants.REPAIR_CAMPAIGN_UPDATE),
                Arguments.of(RepairCampaignController.class, "start",
                        new Class<?>[]{UUID.class}, PermissionConstants.REPAIR_CAMPAIGN_START),
                Arguments.of(RepairCampaignController.class, "complete",
                        new Class<?>[]{UUID.class}, PermissionConstants.REPAIR_CAMPAIGN_COMPLETE),
                Arguments.of(RepairCampaignController.class, "close",
                        new Class<?>[]{UUID.class}, PermissionConstants.REPAIR_CAMPAIGN_CLOSE),
                Arguments.of(RepairCampaignController.class, "cancel",
                        new Class<?>[]{UUID.class, com.toir.dto.repaircampaign.RepairCampaignCancelRequest.class},
                        PermissionConstants.REPAIR_CAMPAIGN_CANCEL),
                Arguments.of(RepairCampaignController.class, "generateWorkOrders",
                        new Class<?>[]{UUID.class, com.toir.dto.repaircampaign.RepairCampaignGenerateWorkOrdersRequest.class,
                                String.class},
                        PermissionConstants.REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS)
        );
    }
}
