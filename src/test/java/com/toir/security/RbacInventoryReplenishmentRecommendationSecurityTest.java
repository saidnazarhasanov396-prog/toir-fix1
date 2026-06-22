package com.toir.security;

import com.toir.controller.InventoryReplenishmentRecommendationController;
import com.toir.service.ReplenishmentProcurementRequestService;
import com.toir.service.InventoryReplenishmentRecommendationService;
import java.util.List;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventoryReplenishmentRecommendationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacInventoryReplenishmentRecommendationSecurityTest.SecurityBeans.class
})
class RbacInventoryReplenishmentRecommendationSecurityTest {

    private static final String URL = "/api/v1/warehouse/replenishment-recommendations";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    InventoryReplenishmentRecommendationService service;

    @MockBean
    ReplenishmentProcurementRequestService procurementRequestService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return props;
        }
    }

    @Test
    void unauthenticatedCannotReadRecommendations() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadRecommendations() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadAloneCannotReadRecommendations() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_EVENT_READ)
    void maintenanceEventReadAloneCannotReadRecommendations() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {PermissionConstants.STOCK_READ, PermissionConstants.MAINTENANCE_EVENT_READ})
    void stockReadAndMaintenanceEventReadCanReadRecommendations() throws Exception {
        when(service.recommendations(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadRecommendations() throws Exception {
        when(service.recommendations(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCannotCreateProcurementFromRecommendations() throws Exception {
        mockMvc.perform(post(URL + "/procurement-requests")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_CREATE)
    void procurementCreateAloneCannotCreateProcurementFromRecommendations() throws Exception {
        mockMvc.perform(post(URL + "/procurement-requests")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validProcurementPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {PermissionConstants.STOCK_READ, PermissionConstants.MAINTENANCE_EVENT_READ})
    void recommendationReadAloneCannotCreateProcurementFromRecommendations() throws Exception {
        mockMvc.perform(post(URL + "/procurement-requests")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validProcurementPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {
            PermissionConstants.PROCUREMENT_CREATE,
            PermissionConstants.STOCK_READ,
            PermissionConstants.MAINTENANCE_EVENT_READ
    })
    void procurementCreateAndRecommendationReadCanCreateProcurementFromRecommendations() throws Exception {
        when(procurementRequestService.createProcurementRequests(any())).thenReturn(List.of());

        mockMvc.perform(post(URL + "/procurement-requests")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validProcurementPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanCreateProcurementFromRecommendations() throws Exception {
        when(procurementRequestService.createProcurementRequests(any())).thenReturn(List.of());

        mockMvc.perform(post(URL + "/procurement-requests")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validProcurementPayload()))
                .andExpect(status().isCreated());
    }

    private String validProcurementPayload() {
        return """
                {
                  "items": [
                    {
                      "sparePartId": "00000000-0000-0000-0000-000000000001",
                      "warehouseId": "00000000-0000-0000-0000-000000000002"
                    }
                  ]
                }
                """;
    }
}
