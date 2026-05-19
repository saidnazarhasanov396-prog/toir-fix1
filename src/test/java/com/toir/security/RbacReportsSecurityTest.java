package com.toir.security;

import com.toir.controller.ReportsController;
import com.toir.service.ReportsService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportsController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacReportsSecurityTest.SecurityBeans.class
})
class RbacReportsSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    ReportsService reportsService;

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
    void unauthenticatedCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ANALYTICS_READ)
    void analyticsReadCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ANALYTICS_EXPORT)
    void analyticsExportCanExportReports() throws Exception {
        when(reportsService.rcmRiskCsv()).thenReturn(new ReportsService.CsvFile("rcm-risk.csv", "id\n"));

        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanExportReports() throws Exception {
        when(reportsService.rcmRiskCsv()).thenReturn(new ReportsService.CsvFile("rcm-risk.csv", "id\n"));

        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanExportReports() throws Exception {
        when(reportsService.rcmRiskCsv()).thenReturn(new ReportsService.CsvFile("rcm-risk.csv", "id\n"));

        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotExportReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/rcm-risk.csv"))
                .andExpect(status().isForbidden());
    }
}
