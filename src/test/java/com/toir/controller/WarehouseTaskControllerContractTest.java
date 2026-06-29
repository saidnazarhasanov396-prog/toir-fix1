package com.toir.controller;

import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskLineDto;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.WarehouseTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseTaskControllerContractTest {

    @Mock
    WarehouseTaskService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseTaskController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsWarehouseTasks() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        WarehouseTaskDto dto = taskDto(UUID.randomUUID(), UUID.randomUUID(), warehouseId);
        when(service.findAll(WarehouseTaskStatus.OPEN, WarehouseTaskType.PUTAWAY, warehouseId, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/warehouse/tasks")
                        .param("status", "OPEN")
                        .param("type", "PUTAWAY")
                        .param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dto.id().toString()))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].lines[0].stockStatus").value("AVAILABLE"));
    }

    @Test
    void createAssignStartScanAndCompleteRoutes() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        WarehouseTaskDto dto = taskDto(taskId, lineId, warehouseId);
        when(service.create(any())).thenReturn(dto);
        when(service.assign(eq(taskId), any())).thenReturn(dto);
        when(service.start(taskId)).thenReturn(dto);
        when(service.scanConfirm(eq(taskId), eq(lineId), any())).thenReturn(dto);
        when(service.complete(eq(taskId), any())).thenReturn(dto);
        when(service.cancel(eq(taskId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/warehouse/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "PUTAWAY",
                                  "warehouseId": "%s",
                                  "sourceType": "PURCHASE_ORDER",
                                  "sourceId": "%s",
                                  "lines": [{
                                    "sparePartId": "%s",
                                    "fromBinId": "%s",
                                    "toBinId": "%s",
                                    "plannedQty": 5.0000,
                                    "unit": "pcs"
                                  }]
                                }
                                """.formatted(warehouseId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()));

        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedToId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/start", taskId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/lines/{lineId}/scan-confirm", taskId, lineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scannedBinId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/complete", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [{"lineId": "%s", "actualQty": 5.0000}],
                                  "comment": "done"
                                }
                                """.formatted(lineId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/cancel", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isOk());
    }

    private WarehouseTaskDto taskDto(UUID taskId, UUID lineId, UUID warehouseId) {
        UUID sparePartId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID toBinId = UUID.randomUUID();
        return new WarehouseTaskDto(
                taskId,
                "WT-2026-00005",
                WarehouseTaskType.PUTAWAY,
                WarehouseTaskStatus.OPEN,
                WarehouseTaskPriority.NORMAL,
                warehouseId,
                WarehouseTaskSourceType.PURCHASE_ORDER,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                "putaway",
                List.of(new WarehouseTaskLineDto(
                        lineId,
                        sparePartId,
                        null,
                        fromBinId,
                        toBinId,
                        "LOT-7",
                        "SN-8",
                        LocalDate.of(2027, 3, 15),
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("5.0000"),
                        BigDecimal.ZERO,
                        "pcs",
                        WarehouseTaskLineStatus.OPEN,
                        false,
                        null
                )),
                Instant.now(),
                Instant.now()
        );
    }
}
