package com.toir.controller;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.dto.plannedshutdown.PlannedShutdownDetailResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetScopeResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownAssetResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemResponse;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemScopeResponse;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.ApprovalService;
import com.toir.service.PlannedShutdownService;
import com.toir.service.repair.RepairCampaignShutdownLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void campaignRelationshipEndpointsDeclareShutdownReadAndMutationPbac() {
        java.util.Map<String, String> expected = java.util.Map.of(
                "getCampaignLink", "PLANNED_SHUTDOWN_READ",
                "listCampaignLinks", "PLANNED_SHUTDOWN_READ",
                "linkCampaign", "PLANNED_SHUTDOWN_UPDATE",
                "unlinkCampaign", "PLANNED_SHUTDOWN_UPDATE");
        for (var method : PlannedShutdownController.class.getDeclaredMethods()) {
            if (!expected.containsKey(method.getName())) continue;
            assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
            assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(expected.get(method.getName()));
        }
        assertThat(java.util.Arrays.stream(PlannedShutdownController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).filter(expected::containsKey)).hasSize(expected.size());
    }

    @Test
    void listCampaignLinksDelegatesVersionAndReturnsPagedTypedRelationships() throws Exception {
        UUID shutdownId = UUID.randomUUID(); UUID campaignId = UUID.randomUUID(); UUID linkId = UUID.randomUUID();
        when(campaignLinkService.listForShutdown(shutdownId, 11L)).thenReturn(List.of(
                new com.toir.dto.repaircampaign.RepairCampaignShutdownLinkResponse(
                        linkId, campaignId, shutdownId, 8L, 11L, true)));

        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/repair-campaigns", shutdownId)
                        .param("version", "11").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(linkId.toString()))
                .andExpect(jsonPath("$.content[0].repairCampaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.content[0].repairCampaignVersion").value(8));
        verify(campaignLinkService).listForShutdown(shutdownId, 11L);
    }

    @Mock
    private PlannedShutdownService service;

    @Mock
    private com.toir.service.plannedshutdown.PlannedShutdownWorkOrderGenerationService workOrderGenerationService;

    @Mock
    private RepairCampaignShutdownLinkService campaignLinkService;

    @Mock
    private ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlannedShutdownController(service, workOrderGenerationService, campaignLinkService))
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
                PlannedShutdownStatus.DRAFT
        );

        when(service.findAllFiltered(eq(departmentId), eq(PlannedShutdownStatus.DRAFT), eq("annual"))).thenReturn(List.of(dto));

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
    void linkedWorkOrdersEndpointReturnsCanonicalShutdownLinks() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto workOrder = new WorkOrderDto(workOrderId, "WO-PS-1", "Pump overhaul",
                UUID.randomUUID(), UUID.randomUUID(), "Pump", "Mechanical", null, null, null, null,
                com.toir.enums.WorkOrderStatus.PLANNED, com.toir.enums.WorkOrderType.PLANNED,
                com.toir.enums.WorkType.REPAIR, PriorityLevel.HIGH, null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(), null, null, 0, 0);
        when(service.linkedWorkOrders(id)).thenReturn(List.of(workOrder));

        mockMvc.perform(get("/api/v1/planned-shutdowns/{id}/work-orders", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workOrderId.toString()))
                .andExpect(jsonPath("$[0].number").value("WO-PS-1"));
        verify(service).linkedWorkOrders(id);
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

    @Test
    void lifecycleEndpointsUseVersionedTypedCommands() throws Exception {
        UUID id = UUID.randomUUID(); UUID departmentId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        var response = detail(id, departmentId, employeeId, PlannedShutdownStatus.PREPARATION);
        when(service.formScope(eq(id), any())).thenReturn(response);
        when(service.beginReadiness(eq(id), any())).thenReturn(response);
        when(service.requestApproval(eq(id), any())).thenReturn(response);
        when(service.prepare(eq(id), any())).thenReturn(response);
        when(service.reschedule(eq(id), any())).thenReturn(response);
        when(service.extend(eq(id), any())).thenReturn(response);

        for (String action : List.of("form-scope", "begin-readiness", "request-approval")) {
            mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/" + action, id)
                            .contentType("application/json")
                            .content("{\"version\":3,\"reason\":\"advance\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/prepare", id)
                        .contentType("application/json")
                        .content("{\"version\":3,\"reason\":\"ready\",\"correlationKey\":\"p-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREPARATION"));
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/reschedule", id)
                        .contentType("application/json")
                        .content("{\"version\":3,\"newStartAt\":\"2026-08-03T00:00:00Z\","
                                + "\"newEndAt\":\"2026-08-04T00:00:00Z\",\"reason\":\"conflict\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/extend", id)
                        .contentType("application/json")
                        .content("{\"version\":3,\"newEndAt\":\"2026-08-05T00:00:00Z\","
                                + "\"reason\":\"emergency\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void everyLifecycleOperationHasAStablePostMapping() {
        java.util.Map<String, String> expected = java.util.Map.ofEntries(
                java.util.Map.entry("formScope", "/{id}/form-scope"),
                java.util.Map.entry("beginReadiness", "/{id}/begin-readiness"),
                java.util.Map.entry("requestApproval", "/{id}/request-approval"),
                java.util.Map.entry("prepare", "/{id}/prepare"),
                java.util.Map.entry("startShutdown", "/{id}/start-shutdown"),
                java.util.Map.entry("confirmSafeState", "/{id}/confirm-safe-state"),
                java.util.Map.entry("startRepair", "/{id}/start-repair"),
                java.util.Map.entry("startTesting", "/{id}/start-testing"),
                java.util.Map.entry("startStartup", "/{id}/start-startup"),
                java.util.Map.entry("complete", "/{id}/complete"),
                java.util.Map.entry("close", "/{id}/close"),
                java.util.Map.entry("cancel", "/{id}/cancel"),
                java.util.Map.entry("reschedule", "/{id}/reschedule"),
                java.util.Map.entry("extend", "/{id}/extend"));
        expected.forEach((name, path) -> {
            var method = java.util.Arrays.stream(PlannedShutdownController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                    .containsExactly(path);
        });
    }

    @Test
    void lifecycleBlockersExposeStableCodesAndAggregateVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.startShutdown(eq(id), any())).thenThrow(new com.toir.exception.PlannedShutdownBlockerException(
                org.springframework.http.HttpStatus.CONFLICT,
                "SHUTDOWN_START_BLOCKED:APPROVAL_SCOPE_STALE,PERMIT_INACTIVE", 17L, List.of(
                new com.toir.dto.plannedshutdown.PlannedShutdownBlocker(
                        "APPROVAL_SCOPE_STALE", "Approval is stale", "PLANNED_SHUTDOWN", id),
                new com.toir.dto.plannedshutdown.PlannedShutdownBlocker(
                        "PERMIT_INACTIVE", "Permit is inactive", "PLANNED_SHUTDOWN", id))));

        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/start-shutdown", id)
                        .contentType("application/json")
                        .content("{\"version\":17,\"reason\":\"start\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.version").value(17))
                .andExpect(jsonPath("$.blockers[0].code").value("APPROVAL_SCOPE_STALE"))
                .andExpect(jsonPath("$.blockers[1].code").value("PERMIT_INACTIVE"));
    }

    @Test
    void lifecycleValidationBlockersUseStableBadRequestPayload() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.extend(eq(id), any())).thenThrow(new com.toir.exception.PlannedShutdownBlockerException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "EXTENSION_REASON_REQUIRED", 9L, List.of(
                new com.toir.dto.plannedshutdown.PlannedShutdownBlocker(
                        "EXTENSION_REASON_REQUIRED", "Reason is required", "PLANNED_SHUTDOWN", id))));

        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/extend", id)
                        .contentType("application/json")
                        .content("{\"version\":9,\"newEndAt\":\"2026-08-05T00:00:00Z\",\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.version").value(9))
                .andExpect(jsonPath("$.blockers[0].code").value("EXTENSION_REASON_REQUIRED"));
    }

    @Test
    void startupEvidenceBlockerMapsItsActualEntityAndCurrentVersion() throws Exception {
        UUID id = UUID.randomUUID(); UUID testId = UUID.randomUUID();
        when(service.startStartup(eq(id), any())).thenThrow(new com.toir.exception.PlannedShutdownBlockerException(
                org.springframework.http.HttpStatus.CONFLICT, "STARTUP_TEST_FAILED", 12L, List.of(
                new com.toir.dto.plannedshutdown.PlannedShutdownBlocker(
                        "STARTUP_TEST_FAILED", "Mandatory startup test failed", "STARTUP_TEST", testId))));

        mockMvc.perform(post("/api/v1/planned-shutdowns/{id}/start-startup", id)
                        .contentType("application/json")
                        .content("{\"version\":12,\"reason\":\"start\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.version").value(12))
                .andExpect(jsonPath("$.blockers[0].code").value("STARTUP_TEST_FAILED"))
                .andExpect(jsonPath("$.blockers[0].entityType").value("STARTUP_TEST"))
                .andExpect(jsonPath("$.blockers[0].entityId").value(testId.toString()));
    }

    private static PlannedShutdownDetailResponse detail(UUID id, UUID departmentId, UUID employeeId,
            PlannedShutdownStatus status) {
        return new PlannedShutdownDetailResponse(id, 3L, "PS-1", "Annual", "PLANNED", departmentId, employeeId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"), "Maintenance",
                "Objective", null, "HIGH", new java.math.BigDecimal("7.5000"), status, 1L, 1L, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, 0L, null, null, List.of(), List.of());
    }
}
