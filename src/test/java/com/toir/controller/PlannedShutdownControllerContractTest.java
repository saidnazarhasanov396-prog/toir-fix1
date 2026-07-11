package com.toir.controller;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.dto.plannedshutdown.PlannedShutdownDetailResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetScopeResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetResponse;
import com.toir.enums.PlannedShutdownAssetDisposition;
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
                                "endAt":"2026-08-02T00:00:00Z","reason":"Maintenance"}
                                """.formatted(departmentId, employeeId)))
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

    private static PlannedShutdownDetailResponse detail(UUID id, UUID departmentId, UUID employeeId,
            PlannedShutdownStatus status) {
        return new PlannedShutdownDetailResponse(id, 3L, "PS-1", "Annual", "PLANNED", departmentId, employeeId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"), "Maintenance",
                "Objective", null, "HIGH", new java.math.BigDecimal("7.5000"), status, 1L, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, 0L, null, null, List.of());
    }
}
