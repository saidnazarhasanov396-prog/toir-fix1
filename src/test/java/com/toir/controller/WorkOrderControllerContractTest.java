package com.toir.controller;

import com.toir.dto.workorder.WorkOrderDto;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
                List.of()
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
}
