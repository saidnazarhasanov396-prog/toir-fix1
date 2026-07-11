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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlannedShutdownController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityAccessService.class,
        RbacPlannedShutdownReadinessSecurityTest.SecurityBeans.class})
class RbacPlannedShutdownReadinessSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean PlannedShutdownService service;

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
}
