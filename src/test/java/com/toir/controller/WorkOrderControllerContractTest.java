package com.toir.controller;

import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.SecurityScope;
import com.toir.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkOrderControllerContractTest {

    @Mock
    WorkOrderService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(service, securityScope))
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
                null
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
                defect
        );
    }

    private RepairRequestBriefDto repairRequestBrief() {
        return new RepairRequestBriefDto(
                UUID.randomUUID(),
                "RR-2026-1001",
                RequestStatus.OPEN,
                PriorityLevel.MEDIUM,
                "Repair request",
                "Short description"
        );
    }

    private DefectBriefDto defectBrief() {
        return new DefectBriefDto(
                UUID.randomUUID(),
                "DEF-2026-1001",
                "Leak",
                DefectStatus.OPEN,
                "HIGH",
                Instant.now()
        );
    }
}
