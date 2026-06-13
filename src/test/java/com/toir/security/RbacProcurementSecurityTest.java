package com.toir.security;

import com.toir.controller.ProcurementRequestController;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.service.ApprovalService;
import com.toir.service.ProcurementRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProcurementRequestController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacProcurementSecurityTest.SecurityBeans.class
})
class RbacProcurementSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    ProcurementRequestService procurementRequestService;

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
    void unauthenticatedCannotReadProcurementRequests() throws Exception {
        mockMvc.perform(get("/api/v1/procurement-requests?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadProcurementRequests() throws Exception {
        mockMvc.perform(get("/api/v1/procurement-requests?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_READ)
    void procurementReadCanReadListAndDetail() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.findAll(null, null,null))
                .thenReturn(List.of(procurementRequestDto(requestId)));
        when(procurementRequestService.findById(requestId))
                .thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(get("/api/v1/procurement-requests?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/procurement-requests/{id}", requestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadProcurementRequests() throws Exception {
        when(procurementRequestService.findAll(null, null,null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/procurement-requests?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadProcurementRequests() throws Exception {
        when(procurementRequestService.findAll(null, null,null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/procurement-requests?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_CREATE)
    void procurementCreateCanCreateRequestsAddLinesAndGenerateLowStockRequests() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.create(any())).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.addLine(eq(requestId), any())).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.generateFromLowStock(null)).thenReturn(List.of(procurementRequestDto(requestId)));

        mockMvc.perform(post("/api/v1/procurement-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementRequestPayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/lines", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementLinePayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests/generate-from-low-stock?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_SUBMIT)
    void procurementSubmitCanSubmitRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.submit(requestId)).thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(post("/api/v1/procurement-requests/{id}/submit", requestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_APPROVE)
    void procurementApproveCanApproveRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.validateCanApprove(requestId)).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.findById(requestId)).thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(post("/api/v1/procurement-requests/{id}/approve", requestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_REJECT)
    void procurementRejectCanRejectRequest() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/procurement-requests/{id}/reject?reason=duplicate", requestId))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_ORDER)
    void procurementOrderCanMarkRequestOrdered() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.markOrdered(requestId)).thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(post("/api/v1/procurement-requests/{id}/ordered", requestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_RECEIVE)
    void procurementReceiveCanMarkRequestReceived() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.markReceived(requestId)).thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(post("/api/v1/procurement-requests/{id}/received", requestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_CANCEL)
    void procurementCancelCanCancelRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.cancel(requestId)).thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(post("/api/v1/procurement-requests/{id}/cancel", requestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_READ)
    void procurementReadCannotMutateRequests() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/procurement-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementRequestPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/lines", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementLinePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/submit", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/approve", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/reject?reason=duplicate", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/ordered", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/received", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/cancel", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/generate-from-low-stock?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateProcurementRequest() throws Exception {
        mockMvc.perform(post("/api/v1/procurement-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementRequestPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutateProcurementRequests() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/procurement-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementRequestPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/approve", requestId))
                .andExpect(status().isForbidden());
    }

    private ProcurementRequestDto procurementRequestDto(UUID id) {
        return new ProcurementRequestDto(
                id,
                "PR-2026-00001",
                "Replace bearing",
                "Procurement request",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                ProcurementRequestStatus.DRAFT,
                "MANUAL",
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private String procurementRequestPayload() {
        return """
                {
                  "title": "Replace bearing",
                  "description": "Procurement request",
                  "requiredBy": "2026-06-01",
                  "lines": []
                }
                """;
    }

    private String procurementLinePayload() {
        return """
                {
                  "sparePartId": "11111111-1111-1111-1111-111111111111",
                  "quantity": 2,
                  "unit": "pcs",
                  "unitPrice": 100,
                  "notes": "Urgent"
                }
                """;
    }
}
