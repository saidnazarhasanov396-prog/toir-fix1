package com.toir.security;

import com.toir.controller.repair.RepairRequestController;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RepairRequestController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacRepairRequestSecurityTest.SecurityBeans.class
})
class RbacRepairRequestSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    RepairRequestService repairRequestService;

    @MockBean
    ApprovalService approvalService;

    @MockBean
    RepairRequestRepository repairRequestRepository;

    @MockBean
    ScopeAccessService scopeAccessService;

    @BeforeEach
    void setUpPbacBypass() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        lenient().when(scopeAccessService.enforceDepartmentScope(any(UUID.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(repairRequestRepository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(repairRequestEntity(invocation.getArgument(0))));
    }

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
    void unauthenticatedCannotReadRepairRequests() throws Exception {
        mockMvc.perform(get("/api/v1/repair-requests?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadRepairRequests() throws Exception {
        mockMvc.perform(get("/api/v1/repair-requests?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_READ)
    void repairRequestReadCanReadListDetailAndStats() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(repairRequestService.search(null, null, null, null, 0, 1, null))
                .thenReturn(new PageImpl<>(List.of(repairRequestDto(requestId)), PageRequest.of(0, 1), 1));
        when(repairRequestService.findById(requestId)).thenReturn(repairRequestDto(requestId));
        when(repairRequestService.getStats(null, null, null))
                .thenReturn(new RepairRequestStatsResponse(1, 0, 1, 0));

        mockMvc.perform(get("/api/v1/repair-requests?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/repair-requests/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadRepairRequests() throws Exception {
        when(repairRequestService.search(null, null, null, null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/repair-requests?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadRepairRequests() throws Exception {
        when(repairRequestService.search(null, null, null, null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/repair-requests?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_CREATE)
    void repairRequestCreateCanCreateRequest() throws Exception {
        when(repairRequestService.create(any())).thenReturn(repairRequestDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_READ)
    void repairRequestReadCannotCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_UPDATE)
    void repairRequestUpdateCanRequestClarificationButCannotUseGenericStatusOverride() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(repairRequestService.requestClarification(requestId, "Need more photos"))
                .thenReturn(repairRequestDto(requestId));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", requestId)
                        .param("status", "IN_REVIEW")
                        .param("reason", "manual correction"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/repair-requests/{id}/request-clarification", requestId)
                        .param("comment", "Need more photos"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_APPROVE)
    void repairRequestApproveEndpointIsRemovedAndCannotUseGenericStatusOverride() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/repair-requests/{id}/approve", requestId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", requestId)
                        .param("status", "APPROVED")
                        .param("reason", "manual correction"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_UPDATE)
    void repairRequestUpdateCannotSetApprovedStatus() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", UUID.randomUUID())
                        .param("status", "APPROVED")
                        .param("reason", "manual correction"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_READ)
    void repairRequestReadCannotChangeStatus() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", UUID.randomUUID())
                        .param("status", "IN_REVIEW")
                        .param("reason", "manual correction"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanUseGenericStatusOverrideWithReason() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(repairRequestService.changeStatus(requestId, RequestStatus.CANCELLED, "duplicate request"))
                .thenReturn(repairRequestDto(requestId));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", requestId)
                        .param("status", "CANCELLED")
                        .param("reason", "duplicate request"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanUseGenericStatusOverrideWithReason() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(repairRequestService.changeStatus(requestId, RequestStatus.CANCELLED, "duplicate request"))
                .thenReturn(repairRequestDto(requestId));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", requestId)
                        .param("status", "CANCELLED")
                        .param("reason", "duplicate request"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_ASSIGN)
    void repairRequestAssignCanAssignRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        when(repairRequestService.assign(requestId, assigneeId)).thenReturn(repairRequestDto(requestId));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/assign", requestId)
                        .param("assigneeId", assigneeId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_READ)
    void repairRequestReadCannotAssignRequest() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests/{id}/assign", UUID.randomUUID())
                        .param("assigneeId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_REJECT)
    void repairRequestRejectCanRejectRequest() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/repair-requests/{id}/reject", requestId)
                        .param("reason", "Invalid request"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_READ)
    void repairRequestReadCannotRejectRequest() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests/{id}/reject", UUID.randomUUID())
                        .param("reason", "Invalid request"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_CLOSE)
    void repairRequestCloseCanCloseRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(repairRequestService.close(eq(requestId), any())).thenReturn(repairRequestDto(requestId));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/close", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closeResult": "Resolved"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.REPAIR_REQUEST_READ)
    void repairRequestReadCannotCloseRequest() throws Exception {
        mockMvc.perform(post("/api/v1/repair-requests/{id}/close", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closeResult": "Resolved"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private static String requestPayload() {
        return """
                {
                  "number": "RR-2026-0001",
                  "title": "Pump vibration",
                  "description": "Excess vibration on pump",
                  "defectId": "%s",
                  "equipmentId": "%s",
                  "departmentId": "%s",
                  "reporterId": "%s",
                  "priority": "HIGH",
                  "criticality": "HIGH",
                  "source": "MANUAL"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static RepairRequestDto repairRequestDto(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration on pump",
                UUID.randomUUID(),
                "Pump",
                UUID.randomUUID(),
                "Maintenance",
                "Workshop",
                UUID.randomUUID(),
                "Reporter",
                null,
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.parse("2026-05-01T09:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private static RepairRequest repairRequestEntity(UUID requestId) {
        RepairRequest entity = new RepairRequest();
        entity.setId(requestId);
        entity.setNumber("RR-2026-0001");
        entity.setTitle("Pump vibration");
        entity.setDescription("Excess vibration on pump");
        entity.setEquipmentId(UUID.randomUUID());
        entity.setDepartmentId(UUID.randomUUID());
        entity.setReporterId(UUID.randomUUID());
        entity.setPriority(PriorityLevel.HIGH);
        entity.setCriticality(CriticalityLevel.HIGH);
        entity.setStatus(RequestStatus.OPEN);
        entity.setSource(RequestSource.MANUAL);
        return entity;
    }
}
