package com.toir.controller;

import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkOrderControllerContractTest {

    @Mock
    WorkOrderService service;

    @Mock
    WorkOrderRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        lenient().when(repository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(workOrderEntity(invocation.getArgument(0), UUID.randomUUID())));
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(service, repository, scopeAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createWithUnknownRepairRequestReturns404() throws Exception {
        when(service.create(any())).thenThrow(RestException.notFound("Repair request not found: " + UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(UUID.randomUUID(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Repair request not found")));
    }

    @Test
    void createWithUnknownDefectReturns404() throws Exception {
        when(service.create(any())).thenThrow(RestException.notFound("Defect not found: " + UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(null, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Defect not found")));
    }

    @Test
    void createWithMismatchedRepairRequestAndDefectReturns400() throws Exception {
        when(service.create(any())).thenThrow(RestException.badRequest("Defect belongs to a different repair request"));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("different repair request")));
    }

    @Test
    void responseIncludesRepairRequestObject() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(workOrderId, repairRequestBrief(), defectBrief());
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest.id").value(response.repairRequest().id().toString()))
                .andExpect(jsonPath("$.repairRequest.number").value(response.repairRequest().number()))
                .andExpect(jsonPath("$.repairRequest.status").value(response.repairRequest().status().name()));
    }

    @Test
    void responseIncludesDefectObject() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(workOrderId, repairRequestBrief(), defectBrief());
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defect.id").value(response.defect().id().toString()))
                .andExpect(jsonPath("$.defect.code").value(response.defect().code()))
                .andExpect(jsonPath("$.defect.status").value(response.defect().status().name()));
    }

    @Test
    void responseWithoutLinksReturnsNullObjects() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(workOrderId, null, null);
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.defect").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void detailResponseIncludesOperationsMaterialsCounts() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.PLANNED,
                repairRequestBrief(),
                defectBrief(),
                2,
                4
        );
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationsCount").value(2))
                .andExpect(jsonPath("$.materialsCount").value(4));
    }

    @Test
    void listWithBlankSearchReturns200() throws Exception {
        WorkOrderDto dto = workOrderDto(UUID.randomUUID(), null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, ""))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dto.id().toString()));
    }

    @Test
    void listResponseIncludesOperationsMaterialsCounts() throws Exception {
        WorkOrderDto dto = workOrderDto(
                UUID.randomUUID(),
                WorkOrderStatus.PLANNED,
                null,
                null,
                3,
                1
        );
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, ""))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operationsCount").value(3))
                .andExpect(jsonPath("$.content[0].materialsCount").value(1));
    }

    @Test
    void listWithMissingLinkedRepairRequestReturnsNullObject() throws Exception {
        UUID missingRepairRequestId = UUID.randomUUID();
        WorkOrderDto dto = workOrderDtoWithIds(UUID.randomUUID(), missingRepairRequestId, null, null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, ""))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repairRequestId").value(missingRepairRequestId.toString()))
                .andExpect(jsonPath("$.content[0].repairRequest").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listWithMissingLinkedDefectReturnsNullObject() throws Exception {
        UUID missingDefectId = UUID.randomUUID();
        WorkOrderDto dto = workOrderDtoWithIds(UUID.randomUUID(), null, missingDefectId, null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, ""))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].defectId").value(missingDefectId.toString()))
                .andExpect(jsonPath("$.content[0].defect").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listWithNoDataReturnsStablePage() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, ""))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void startResponseShowsSyncedRepairRequestStatus() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.IN_PROGRESS,
                repairRequestBrief(RequestStatus.IN_PROGRESS),
                defectBrief(DefectStatus.OPEN)
        );
        when(service.start(workOrderId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/start", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.repairRequest.status").value("IN_PROGRESS"));
    }

    @Test
    void startResponseShowsSyncedDefectStatus() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.IN_PROGRESS,
                repairRequestBrief(RequestStatus.OPEN),
                defectBrief(DefectStatus.IN_PROGRESS)
        );
        when(service.start(workOrderId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/start", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.defect.status").value("IN_PROGRESS"));
    }

    @Test
    void completeResponseShowsResolvedDefectWhenEligible() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.COMPLETED,
                repairRequestBrief(RequestStatus.OPEN),
                defectBrief(DefectStatus.RESOLVED)
        );
        when(service.complete(eq(workOrderId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.defect.status").value("RESOLVED"));
    }

    @Test
    void closeResponseShowsClosedRepairRequestWhenEligible() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.CLOSED,
                repairRequestBrief(RequestStatus.CLOSED),
                defectBrief(DefectStatus.CLOSED)
        );
        when(service.close(eq(workOrderId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/close", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "closed",
                                  "closureNotes": "notes"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.repairRequest.status").value("CLOSED"));
    }

    @Test
    void completeReplacementWithoutOldEquipmentReturnWarehouseIdReturnsBadRequest() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.complete(eq(workOrderId), any()))
                .thenThrow(RestException.badRequest("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT"));

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT"));
    }

    @Test
    void completeNonReplacementWithOldEquipmentReturnWarehouseIdReturnsBadRequest() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.complete(eq(workOrderId), any()))
                .thenThrow(RestException.badRequest("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT"));

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary",
                                  "oldEquipmentReturnWarehouseId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT"));
    }

    @Test
    void completeReplacementWithOldEquipmentReturnWarehouseIdReturnsOk() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID returnWarehouseId = UUID.randomUUID();
        WorkOrderDto response = new WorkOrderDto(
                workOrderId,
                "WO-2026-0001",
                "Replacement work order",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                WorkOrderStatus.COMPLETED,
                WorkOrderType.PLANNED,
                WorkType.REPLACEMENT,
                null,
                null,
                null,
                null,
                null,
                "summary",
                "done",
                null,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Replacement Equipment",
                List.of(),
                null,
                null,
                0,
                0
        );
        when(service.complete(eq(workOrderId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary",
                                  "oldEquipmentReturnWarehouseId": "%s"
                                }
                                """.formatted(returnWarehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workOrderId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.workType").value("REPLACEMENT"));
    }

    private WorkOrder workOrderEntity(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Planned repair");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.PLANNED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private String baseCreateRequestJson(UUID repairRequestId, UUID defectId) {
        String repairRequestPart = repairRequestId == null
                ? ""
                : """
                  "repairRequestId": "%s",
                """.formatted(repairRequestId);
        String defectPart = defectId == null
                ? ""
                : """
                  "defectId": "%s",
                """.formatted(defectId);
        return """
                {
                  "number": "WO-2026-1001",
                  "title": "Planned repair",
                  "equipmentId": "%s",
                  "departmentId": "%s",
                %s
                %s
                  "type": "PLANNED",
                  "workType": "REPAIR",
                  "priority": "MEDIUM",
                  "createdById": "%s",
                  "summary": "summary"
                }
                """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                repairRequestPart,
                defectPart,
                UUID.randomUUID()
        );
    }

    private WorkOrderDto workOrderDto(UUID id, RepairRequestBriefDto repairRequest, DefectBriefDto defect) {
        return workOrderDto(id, WorkOrderStatus.PLANNED, repairRequest, defect);
    }

    private WorkOrderDto workOrderDto(UUID id,
                                      WorkOrderStatus status,
                                      RepairRequestBriefDto repairRequest,
                                      DefectBriefDto defect) {
        return workOrderDto(id, status, repairRequest, defect, 0, 0);
    }

    private WorkOrderDto workOrderDto(UUID id,
                                      WorkOrderStatus status,
                                      RepairRequestBriefDto repairRequest,
                                      DefectBriefDto defect,
                                      int operationsCount,
                                      int materialsCount) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                repairRequest == null ? null : repairRequest.id(),
                defect == null ? null : defect.id(),
                null,
                null,
                status,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                repairRequest,
                defect,
                operationsCount,
                materialsCount
        );
    }

    private WorkOrderDto workOrderDtoWithIds(UUID id,
                                             UUID repairRequestId,
                                             UUID defectId,
                                             RepairRequestBriefDto repairRequest,
                                             DefectBriefDto defect) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                repairRequestId,
                defectId,
                null,
                null,
                WorkOrderStatus.PLANNED,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                repairRequest,
                defect,
                0,
                0
        );
    }

    private RepairRequestBriefDto repairRequestBrief() {
        return repairRequestBrief(RequestStatus.OPEN);
    }

    private RepairRequestBriefDto repairRequestBrief(RequestStatus status) {
        return new RepairRequestBriefDto(
                UUID.randomUUID(),
                "RR-2026-1001",
                status,
                PriorityLevel.MEDIUM,
                "Repair request",
                "Short description"
        );
    }

    private DefectBriefDto defectBrief() {
        return defectBrief(DefectStatus.OPEN);
    }

    private DefectBriefDto defectBrief(DefectStatus status) {
        return new DefectBriefDto(
                UUID.randomUUID(),
                "DEF-2026-1001",
                "Leak",
                status,
                "HIGH",
                Instant.now()
        );
    }

    @Test
    void statsShouldReturn200AndStatsPayload() throws Exception {
        com.toir.dto.workorder.WorkOrderStatsResponse statsResponse = new com.toir.dto.workorder.WorkOrderStatsResponse(10, 5, 4, 1);

        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.getStats(isNull(), isNull(), isNull(), isNull())).thenReturn(statsResponse);

        mockMvc.perform(get("/api/v1/work-orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(10))
                .andExpect(jsonPath("$.openOrders").value(5))
                .andExpect(jsonPath("$.completedOrders").value(4))
                .andExpect(jsonPath("$.overdueOrders").value(1));

        verify(service).getStats(null, null, null, null);
    }
}
