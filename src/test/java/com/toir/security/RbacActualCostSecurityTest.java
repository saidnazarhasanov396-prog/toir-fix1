package com.toir.security;

import com.toir.controller.ActualCostController;
import com.toir.dto.actualcost.ActualCostDto;
import com.toir.enums.ActualCostStatus;
import com.toir.service.ApprovalService;
import com.toir.service.ActualCostService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActualCostController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacActualCostSecurityTest.SecurityBeans.class
})
class RbacActualCostSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    ActualCostService actualCostService;

    @MockBean
    ApprovalService approvalService;

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
    void unauthenticatedCannotReadActualCosts() throws Exception {
        mockMvc.perform(get("/api/v1/actual-costs?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadActualCosts() throws Exception {
        mockMvc.perform(get("/api/v1/actual-costs?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_READ)
    void actualCostReadCanReadListAndPending() throws Exception {
        when(actualCostService.findByFilters(null, null)).thenReturn(List.of());
        when(actualCostService.findPending()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/actual-costs?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/actual-costs/pending?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadActualCosts() throws Exception {
        when(actualCostService.findByFilters(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/actual-costs?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadActualCosts() throws Exception {
        when(actualCostService.findByFilters(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/actual-costs?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_CREATE)
    void actualCostCreateCanCreateActualCost() throws Exception {
        when(actualCostService.create(any(ActualCostDto.class))).thenReturn(actualCostDto(ActualCostStatus.PENDING));

        mockMvc.perform(post("/api/v1/actual-costs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualCostPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_APPROVE)
    void actualCostApproveEndpointIsRemoved() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs/{id}/approve", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "Approved"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_REJECT)
    void actualCostRejectCanRejectActualCost() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "Rejected"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ACTUAL_COST_READ)
    void actualCostReadCannotCreateApproveOrReject() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualCostPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/actual-costs/{id}/approve", id)
                        .param("reviewerId", reviewerId.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "Rejected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateActualCost() throws Exception {
        mockMvc.perform(post("/api/v1/actual-costs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualCostPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateActualCost() throws Exception {
        mockMvc.perform(post("/api/v1/actual-costs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualCostPayload()))
                .andExpect(status().isForbidden());
    }

    private ActualCostDto actualCostDto(ActualCostStatus status) {
        return new ActualCostDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                null,
                null,
                null,
                100.0,
                Instant.now(),
                "Test actual cost"
        );
    }

    private String actualCostPayload() {
        return """
                {
                  "workOrderId": "%s",
                  "budgetLineId": "%s",
                  "costCategoryId": "%s",
                  "amount": 100,
                  "costDate": "2026-05-01T00:00:00Z",
                  "notes": "Test actual cost"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
