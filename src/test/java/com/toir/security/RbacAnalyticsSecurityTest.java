package com.toir.security;

import com.toir.controller.AnalyticsController;
import com.toir.controller.DashboardController;
import com.toir.controller.DowntimeAnalyticsCompatibilityController;
import com.toir.controller.ParetoController;
import com.toir.service.AnalyticsService;
import com.toir.service.DashboardService;
import com.toir.service.ParetoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        DashboardController.class,
        AnalyticsController.class,
        DowntimeAnalyticsCompatibilityController.class,
        ParetoController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacAnalyticsSecurityTest.SecurityBeans.class
})
class RbacAnalyticsSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    DashboardService dashboardService;

    @MockBean
    AnalyticsService analyticsService;

    @MockBean
    ParetoService paretoService;

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
    void unauthenticatedCannotReadDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ANALYTICS_READ)
    void analyticsReadCanReadDashboardAndAnalytics() throws Exception {
        when(analyticsService.reliabilityList()).thenReturn(List.of());
        when(paretoService.downtimeCauses(any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/dashboards/overview"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/reliability?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/pareto/downtime-causes?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ANALYTICS_READ)
    void analyticsReadCanUseDowntimeCompatibilityRoute() throws Exception {
        mockMvc.perform(get("/api/v1/downtime-analytics/equipment/{equipmentId}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isForbidden());
    }
}
