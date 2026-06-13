package com.toir.security;

import com.toir.controller.repair.RepairRequestController;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RepairRequestPbacScopeTest {

    @Mock
    RepairRequestService service;

    @Mock
    RepairRequestRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    ApprovalService approvalService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RepairRequestController(service, approvalService, repository, scopeAccessService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listClampsRequestedDepartmentToCurrentDepartment() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(service.search(null, currentDepartmentId, null, null, 0, 20, null))
                .thenReturn(new PageImpl<>(List.of(dto(UUID.randomUUID(), currentDepartmentId, UUID.randomUUID(), null)),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/repair-requests")
                        .param("departmentId", requestedDepartmentId.toString()))
                .andExpect(status().isOk());

        verify(service).search(null, currentDepartmentId, null, null, 0, 20, null);
    }

    @Test
    void statsWithoutDepartmentForNonAdminWithoutDepartmentIsDenied() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        mockMvc.perform(get("/api/v1/repair-requests/stats"))
                .andExpect(status().isForbidden());

        verify(service, never()).getStats(any(), any(), any());
    }

    @Test
    void systemAdminCanRequestGlobalList() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(service.search(null, null, null, null, 0, 20, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/repair-requests"))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, null, 0, 20, null);
    }

    @Test
    void detailAllowsSameDepartment() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, UUID.randomUUID(), null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.findById(requestId)).thenReturn(dto(requestId, departmentId, UUID.randomUUID(), null));

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk());
    }

    @Test
    void detailDeniesDifferentDepartment() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, UUID.randomUUID(), null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isForbidden());

        verify(service, never()).findById(requestId);
    }

    @Test
    void reporterCanReadOwnRequestWithoutDepartmentMatch() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, reporterId, null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(reporterId);
        when(service.findById(requestId)).thenReturn(dto(requestId, departmentId, reporterId, null));

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk());
    }

    @Test
    void missingRequestRemainsNotFound() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDeniesDifferentDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content(requestPayload(departmentId, UUID.randomUUID())))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    void createAllowsSameDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.create(any())).thenReturn(dto(UUID.randomUUID(), departmentId, UUID.randomUUID(), null));

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content(requestPayload(departmentId, UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    void changeStatusDeniesDifferentDepartmentEvenForReporter() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, reporterId, null)));
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", requestId)
                        .param("status", "IN_REVIEW")
                        .param("reason", "manual correction"))
                .andExpect(status().isForbidden());

        verify(service, never()).changeStatus(any(), any(), any());
    }

    @Test
    void assignChecksDepartmentScopeBeforeMutation() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, UUID.randomUUID(), null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.assign(requestId, assigneeId)).thenReturn(dto(requestId, departmentId, UUID.randomUUID(), assigneeId));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/assign", requestId)
                        .param("assigneeId", assigneeId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void closeDeniesDifferentDepartment() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, UUID.randomUUID(), null)));
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/repair-requests/{id}/close", requestId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "closeResult": "Resolved"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(service, never()).close(eq(requestId), any(CloseRequestRequest.class));
    }

    @Test
    void rejectDeniesDifferentDepartment() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, UUID.randomUUID(), null)));
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/repair-requests/{id}/reject", requestId)
                        .param("reason", "Invalid request"))
                .andExpect(status().isForbidden());

        verify(service, never()).reject(requestId, "Invalid request");
    }

    @Test
    void requestClarificationAllowsSameDepartment() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest(requestId, departmentId, UUID.randomUUID(), null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.requestClarification(requestId, "Need more photos"))
                .thenReturn(dto(requestId, departmentId, UUID.randomUUID(), null));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/request-clarification", requestId)
                        .param("comment", "Need more photos"))
                .andExpect(status().isOk());
    }

    private void doDenyDepartment(UUID departmentId) {
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
    }

    private RepairRequest repairRequest(UUID id, UUID departmentId, UUID reporterId, UUID assignedToId) {
        RepairRequest request = new RepairRequest();
        request.setId(id);
        request.setNumber("RR-2026-0001");
        request.setTitle("Pump vibration");
        request.setDescription("Excess vibration");
        request.setEquipmentId(UUID.randomUUID());
        request.setDepartmentId(departmentId);
        request.setReporterId(reporterId);
        request.setAssignedToId(assignedToId);
        request.setPriority(PriorityLevel.HIGH);
        request.setCriticality(CriticalityLevel.HIGH);
        request.setStatus(RequestStatus.OPEN);
        request.setSource(RequestSource.MANUAL);
        return request;
    }

    private RepairRequestDto dto(UUID id, UUID departmentId, UUID reporterId, UUID assignedToId) {
        return new RepairRequestDto(
                id,
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration",
                UUID.randomUUID(),
                "Pump",
                departmentId,
                "Maintenance",
                null,
                reporterId,
                "Reporter",
                assignedToId,
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

    private String requestPayload(UUID departmentId, UUID reporterId) {
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
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), departmentId, reporterId);
    }
}
