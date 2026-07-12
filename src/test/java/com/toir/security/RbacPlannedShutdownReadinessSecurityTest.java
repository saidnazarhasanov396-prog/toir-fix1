package com.toir.security;

import com.toir.controller.PlannedShutdownController;
import com.toir.service.PlannedShutdownService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = PlannedShutdownController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityAccessService.class,
        RbacPlannedShutdownReadinessSecurityTest.SecurityBeans.class})
class RbacPlannedShutdownReadinessSecurityTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.toir.service.repair.RepairCampaignShutdownLinkService campaignLinkService;
    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean PlannedShutdownService service;
    @MockBean com.toir.service.plannedshutdown.PlannedShutdownWorkOrderGenerationService workOrderGenerationService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean CorsProperties corsProperties() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return props;
        }
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_READ)
    void readPermissionCannotCompleteReadinessOrVerifyIsolation() throws Exception {
        performComplete().andExpect(status().isForbidden());
        performVerify().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_PREPARE)
    void preparePermissionCanCompleteReadinessButCannotVerifyIsolation() throws Exception {
        performComplete().andExpect(status().isOk());
        performVerify().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_CONFIRM_SAFE_STATE)
    void safeStatePermissionCanVerifyIsolationButCannotCompleteReadiness() throws Exception {
        performVerify().andExpect(status().isOk());
        performComplete().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_UPDATE)
    void updatePermissionCannotRequestApproval() throws Exception {
        performRequestApproval().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_REQUEST_APPROVAL)
    void requestApprovalPermissionCanRequestApproval() throws Exception {
        performRequestApproval().andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_PREPARE)
    void preparePermissionCanGenerateShutdownWorkOrders() throws Exception {
        performGenerate().andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_UPDATE)
    void updatePermissionCannotGenerateShutdownWorkOrders() throws Exception {
        performGenerate().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_TEST)
    void testPermissionCanRecordStartupResultButCannotApproveProductionReturn() throws Exception {
        performStartupResult().andExpect(status().isOk());
        performProductionReturn().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_STARTUP)
    void startupPermissionCanApproveProductionReturnButCannotRecordTest() throws Exception {
        performProductionReturn().andExpect(status().isCreated());
        performStartupResult().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_READ)
    void readPermissionCanReadClosureReportButCannotMutateEvidence() throws Exception {
        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/closure-report", UUID.randomUUID()))
                .andExpect(status().isOk());
        performStartupResult().andExpect(status().isForbidden());
        performProductionReturn().andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_READ)
    void readPermissionCanListCanonicalWorkItems() throws Exception {
        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/work-items", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_UPDATE)
    void updatePermissionAloneCannotReadCanonicalWorkItems() throws Exception {
        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/work-items", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PLANNED_SHUTDOWN_PREPARE)
    void operationalPermissionWithoutExtendCannotCreateExtensionHistory() throws Exception {
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/extend", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"version\":7,\"newEndAt\":\"2026-08-05T00:00:00Z\","
                                + "\"reason\":\"emergency\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).extend(any(), any());
    }

    private org.springframework.test.web.servlet.ResultActions performComplete() throws Exception {
        return mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/readiness/{itemId}/complete",
                UUID.randomUUID(), UUID.randomUUID()).contentType("application/json").content("{\"version\":1}"));
    }

    private org.springframework.test.web.servlet.ResultActions performVerify() throws Exception {
        return mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/isolation/{pointId}/verify",
                UUID.randomUUID(), UUID.randomUUID()).contentType("application/json").content("{\"version\":1}"));
    }

    private org.springframework.test.web.servlet.ResultActions performRequestApproval() throws Exception {
        return mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/request-approval", UUID.randomUUID())
                .contentType("application/json").content("{\"version\":1,\"reason\":\"ready\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions performGenerate() throws Exception {
        return mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/work-orders/generate", UUID.randomUUID())
                .header("Idempotency-Key", "security-generation-1")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private org.springframework.test.web.servlet.ResultActions performStartupResult() throws Exception {
        return mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/startup-tests/{testId}/result",
                UUID.randomUUID(), UUID.randomUUID()).contentType("application/json")
                .content("{\"version\":1,\"measuredValue\":\"42.0000\",\"unit\":\"bar\","
                        + "\"passed\":true,\"evidence\":\"asset-1\",\"performerId\":\""
                        + UUID.randomUUID() + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions performProductionReturn() throws Exception {
        return mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/production-return", UUID.randomUUID())
                .contentType("application/json").content("{\"version\":1,\"evidence\":\"stable\"}"));
    }
}
