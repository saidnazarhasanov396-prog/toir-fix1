package com.toir.security;

import com.toir.controller.ActualCostReviewRouteOverrideController;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.service.ActualCostReviewRouteOverrideService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActualCostReviewRouteOverrideController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacFinancialRouteOverrideSecurityTest.SecurityBeans.class
})
class RbacFinancialRouteOverrideSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    ActualCostReviewRouteOverrideService overrideService;

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
    void unauthenticatedCannotReadRouteOverrides() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadRouteOverrides() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.FINANCE_ROUTE_OVERRIDE_READ)
    void routeOverrideReadCanReadListAndByActualCost() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        when(overrideService.findActive()).thenReturn(List.of());
        when(overrideService.findByActualCost(actualCostId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides/by-actual-cost/{actualCostId}", actualCostId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadRouteOverrides() throws Exception {
        when(overrideService.findActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadRouteOverrides() throws Exception {
        when(overrideService.findActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-route-overrides?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.FINANCE_ROUTE_OVERRIDE_APPLY)
    void routeOverrideApplyCanApplyOverride() throws Exception {
        mockMvc.perform(post("/api/v1/budgets/actual-costs/review-route-overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeOverridePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.FINANCE_ROUTE_OVERRIDE_CLEAR)
    void routeOverrideClearCanDeactivateOverride() throws Exception {
        mockMvc.perform(post("/api/v1/budgets/actual-costs/review-route-overrides/{id}/deactivate", UUID.randomUUID())
                        .param("userId", UUID.randomUUID().toString())
                        .param("comment", "Clear override"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.FINANCE_ROUTE_OVERRIDE_READ)
    void routeOverrideReadCannotApplyOrClearOverride() throws Exception {
        mockMvc.perform(post("/api/v1/budgets/actual-costs/review-route-overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeOverridePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/budgets/actual-costs/review-route-overrides/{id}/deactivate", UUID.randomUUID())
                        .param("userId", UUID.randomUUID().toString())
                        .param("comment", "Clear override"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotApplyRouteOverride() throws Exception {
        mockMvc.perform(post("/api/v1/budgets/actual-costs/review-route-overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeOverridePayload()))
                .andExpect(status().isForbidden());
    }

    private String routeOverridePayload() {
        return """
                {
                  "actualCostId": "%s",
                  "approvalRoleCode": "FIN_MANAGER",
                  "thresholdHours": 24,
                  "comment": "Override route"
                }
                """.formatted(UUID.randomUUID());
    }
}
