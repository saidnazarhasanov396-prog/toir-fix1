package com.toir.security;

import com.toir.controller.ApprovalController;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.enums.ApprovalStatus;
import com.toir.service.ApprovalService;
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

@WebMvcTest(controllers = ApprovalController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacApprovalSecurityTest.SecurityBeans.class
})
class RbacApprovalSecurityTest {

    private static final String APPROVAL_READ = "APPROVAL_READ";
    private static final String APPROVAL_CREATE = "APPROVAL_CREATE";
    private static final String APPROVAL_APPROVE = "APPROVAL_APPROVE";
    private static final String APPROVAL_REJECT = "APPROVAL_REJECT";
    private static final String APPROVAL_CANCEL = "APPROVAL_CANCEL";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

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
    void unauthenticatedCannotReadApprovals() throws Exception {
        mockMvc.perform(get("/api/v1/approvals?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadApprovals() throws Exception {
        mockMvc.perform(get("/api/v1/approvals?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = APPROVAL_READ)
    void approvalReadCanReadListAndDetail() throws Exception {
        UUID approvalId = UUID.randomUUID();
        when(approvalService.pending()).thenReturn(List.of(approvalDto(approvalId)));
        when(approvalService.findById(approvalId)).thenReturn(approvalDto(approvalId));

        mockMvc.perform(get("/api/v1/approvals?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/approvals/{id}", approvalId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadApprovals() throws Exception {
        when(approvalService.pending()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/approvals?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadApprovals() throws Exception {
        when(approvalService.pending()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/approvals?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = APPROVAL_CREATE)
    void approvalCreateCanCreateApproval() throws Exception {
        when(approvalService.create(any())).thenReturn(approvalDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApprovalPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = APPROVAL_APPROVE)
    void approvalApproveCanApproveApproval() throws Exception {
        UUID approvalId = UUID.randomUUID();
        when(approvalService.approve(any(), any())).thenReturn(approvalDto(approvalId));

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = APPROVAL_REJECT)
    void approvalRejectCanRejectApproval() throws Exception {
        UUID approvalId = UUID.randomUUID();
        when(approvalService.reject(any(), any())).thenReturn(approvalDto(approvalId));

        mockMvc.perform(post("/api/v1/approvals/{id}/reject", approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = APPROVAL_CANCEL)
    void approvalCancelCanCancelApproval() throws Exception {
        UUID approvalId = UUID.randomUUID();
        when(approvalService.cancel(approvalId)).thenReturn(approvalDto(approvalId));

        mockMvc.perform(post("/api/v1/approvals/{id}/cancel", approvalId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = APPROVAL_READ)
    void approvalReadCannotMutateApprovals() throws Exception {
        UUID approvalId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApprovalPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/approvals/{id}/reject", approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/approvals/{id}/cancel", approvalId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotMutateApprovals() throws Exception {
        mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApprovalPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutateApprovals() throws Exception {
        UUID approvalId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload()))
                .andExpect(status().isForbidden());
    }

    private ApprovalRequestDto approvalDto(UUID id) {
        return new ApprovalRequestDto(
                id,
                "WORK_ORDER",
                UUID.randomUUID(),
                "Approve work order",
                UUID.randomUUID(),
                ApprovalStatus.PENDING,
                1,
                null,
                "Approval request",
                null,
                List.of()
        );
    }

    private String createApprovalPayload() {
        return """
                {
                  "documentType": "WORK_ORDER",
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "title": "Approve work order",
                  "requesterId": "22222222-2222-2222-2222-222222222222",
                  "description": "Approval request",
                  "steps": [
                    {
                      "approverId": "33333333-3333-3333-3333-333333333333",
                      "approverRole": "CHIEF_MECHANIC"
                    }
                  ]
                }
                """;
    }

    private String decisionPayload() {
        return """
                {
                  "approverId": "33333333-3333-3333-3333-333333333333",
                  "comment": "Reviewed"
                }
                """;
    }
}
