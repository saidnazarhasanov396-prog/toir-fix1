package com.toir.controller;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.dto.plannedshutdown.PlannedShutdownDetailResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetScopeResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemScopeResponse;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PlanStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ApprovalService;
import com.toir.service.PlannedShutdownService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownControllerContractTest {

    @Mock
    private PlannedShutdownService service;

    @Mock
    private ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlannedShutdownController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShutdownsWithFiltersReturnsFilteredPage() throws Exception {
        UUID shutdownId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        
        PlannedShutdownDto dto = new PlannedShutdownDto(
                shutdownId,
                "Annual Maintenance",
                departmentId,
                Instant.parse("2026-05-19T10:00:00Z"),
                Instant.parse("2026-05-19T18:00:00Z"),
                "Routine check",
                PlanStatus.DRAFT
        );

        when(service.findAllFiltered(eq(departmentId), eq(PlanStatus.DRAFT), eq("annual"))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/planned-shutdowns")
                        .param("departmentId", departmentId.toString())
                        .param("status", "DRAFT")
                        .param("search", "annual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(shutdownId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Annual Maintenance"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()));
    }

    @Test
    void createUsesTypedLifecycleContractWithoutLegacyPlanStatus() throws Exception {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        when(service.create(any())).thenReturn(detail(id, departmentId, employeeId, PlannedShutdownStatus.SCOPE_FORMATION));

        mockMvc.perform(post("/api/v1/planned-shutdowns")
                        .contentType("application/json")
                        .content("""
                                {"name":"Annual","shutdownType":"PLANNED","departmentId":"%s",\
                                "responsibleEmployeeId":"%s","startAt":"2026-08-01T00:00:00Z",\
                                "endAt":"2026-08-02T00:00:00Z","reason":"Maintenance",\
                                "assets":[{"equipmentId":"%s","disposition":"STOPPED","orderNumber":0}]}
                                """.formatted(departmentId, employeeId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCOPE_FORMATION"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.responsibleEmployeeId").value(employeeId.toString()));
    }

    @Test
    void detailUpdateAndScopeEndpointsPropagateVersionsAndStableAssetIds() throws Exception {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
        when(service.get(id)).thenReturn(detail(id, departmentId, employeeId, PlannedShutdownStatus.DRAFT));
        when(service.update(eq(id), any())).thenReturn(detail(id, departmentId, employeeId, PlannedShutdownStatus.DRAFT));
        var scope = new PlannedShutdownAssetScopeResponse(id, 4L, 2L, List.of(
                new PlannedShutdownAssetResponse(assetId, equipmentId, PlannedShutdownAssetDisposition.STOPPED, "main", 0)));
        when(service.getAssets(id)).thenReturn(scope);
        when(service.replaceAssets(eq(id), any())).thenReturn(scope);

        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mockMvc.perform(put("/api/v1/planned-shutdowns/{id}", id).contentType("application/json").content("""
                {"version":3,"code":"PS-1","name":"Annual","shutdownType":"PLANNED",\
                "departmentId":"%s","responsibleEmployeeId":"%s","startAt":"2026-08-01T00:00:00Z",\
                "endAt":"2026-08-02T00:00:00Z","reason":"Maintenance"}
                """.formatted(departmentId, employeeId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("PS-1"));
        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/assets", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scopeVersion").value(2))
                .andExpect(jsonPath("$.assets[0].id").value(assetId.toString()));
        mockMvc.perform(put("/api/v1/planned-shutdowns/{id}/assets", id).contentType("application/json").content("""
                {"version":3,"assets":[{"equipmentId":"%s","disposition":"STOPPED","orderNumber":0}]}
                """.formatted(equipmentId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(4));

        verify(service).replaceAssets(eq(id), any());
    }

    @Test
    void workItemCrudAndReorderEndpointsExposeTypedScopeVersions() throws Exception {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        var response = new PlannedShutdownWorkItemScopeResponse(id, 5L, 3L, List.of(
                new PlannedShutdownWorkItemResponse(itemId, PlannedShutdownWorkItemSourceType.MANUAL, null,
                        equipmentId, "Inspect bearing", PriorityLevel.HIGH, true, true, 45, "A", 0,
                        PlannedShutdownItemStatus.PENDING)));
        when(service.listWorkItems(id)).thenReturn(response);
        when(service.addWorkItem(eq(id), any())).thenReturn(response);
        when(service.updateWorkItem(eq(id), eq(itemId), any())).thenReturn(response);
        when(service.removeWorkItem(id, itemId, 5L)).thenReturn(response);
        when(service.reorderWorkItems(eq(id), any())).thenReturn(response);

        String body = """
                {"version":5,"sourceType":"MANUAL","equipmentId":"%s","title":"Inspect bearing",\
                 "priority":"HIGH","requiresShutdown":true,"requiresIsolation":true,\
                 "plannedDurationMinutes":45,"criticality":"A","orderNumber":0}
                """.formatted(equipmentId);
        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/work-items", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scopeVersion").value(3));
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/work-items", id)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workItems[0].sourceType").value("MANUAL"));
        mockMvc.perform(put("/api/v1/planned-shutdowns/{id}/work-items/{itemId}", id, itemId)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/planned-shutdowns/{id}/work-items/{itemId}", id, itemId)
                        .param("version", "5"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/planned-shutdowns/{id}/work-items/reorder", id)
                        .contentType("application/json")
                        .content("{\"version\":5,\"itemIds\":[\"" + itemId + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void readinessAndIsolationActionsExposeTypedEvidenceContracts() throws Exception {
        UUID id = UUID.randomUUID(); UUID itemId = UUID.randomUUID(); UUID pointId = UUID.randomUUID();
        var readiness = new com.toir.dto.plannedshutdown.PlannedShutdownReadinessScopeResponse(id, 5L, 3L, List.of());
        var isolation = new com.toir.dto.plannedshutdown.PlannedShutdownIsolationScopeResponse(id, 5L, 3L, List.of());
        when(service.listReadiness(id)).thenReturn(readiness);
        when(service.completeReadinessItem(eq(id), eq(itemId), any())).thenReturn(readiness);
        when(service.reopenReadinessItem(eq(id), eq(itemId), any())).thenReturn(readiness);
        when(service.applyIsolation(eq(id), eq(pointId), any())).thenReturn(isolation);
        when(service.verifyIsolation(eq(id), eq(pointId), any())).thenReturn(isolation);
        when(service.releaseIsolation(eq(id), eq(pointId), any())).thenReturn(isolation);

        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/readiness", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scopeVersion").value(3));
        String readinessBody = "{\"version\":5,\"evidence\":\"photo:1\",\"comment\":\"checked\"}";
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/readiness/{itemId}/complete", id, itemId)
                        .contentType("application/json").content(readinessBody)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/readiness/{itemId}/reopen", id, itemId)
                        .contentType("application/json").content(readinessBody)).andExpect(status().isOk());
        String action = "{\"version\":5}";
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/isolation/{pointId}/apply", id, pointId)
                        .contentType("application/json").content(action)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/isolation/{pointId}/verify", id, pointId)
                        .contentType("application/json").content(action)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/isolation/{pointId}/release", id, pointId)
                        .contentType("application/json").content(action)).andExpect(status().isOk());
    }

    private static PlannedShutdownDetailResponse detail(UUID id, UUID departmentId, UUID employeeId,
            PlannedShutdownStatus status) {
        return new PlannedShutdownDetailResponse(id, 3L, "PS-1", "Annual", "PLANNED", departmentId, employeeId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"), "Maintenance",
                "Objective", null, "HIGH", new java.math.BigDecimal("7.5000"), status, 1L, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, 0L, null, null, List.of(), List.of());
    }
}
