package com.toir.controller;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnDto;
import com.toir.enums.ReservationStatus;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.WorkOrderWmsService;
import com.toir.service.warehouse.WmsOperationsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkOrderWmsControllerContractTest {

    @Mock
    WorkOrderWmsService service;

    @Mock
    WmsOperationsQueryService operationsQueryService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkOrderWmsController(service, operationsQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reservePickListAndConfirmPickRoutesReturnWorkflowDtos() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID pickListId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();

        when(service.reserve(eq(workOrderId), any())).thenReturn(List.of(new ReservationDto(
                reservationId,
                null,
                warehouseId,
                sparePartId,
                binId,
                workOrderId,
                null,
                UUID.randomUUID(),
                requirementId,
                "LOT-7",
                "SN-8",
                null,
                WarehouseStockStatus.AVAILABLE,
                4,
                ReservationStatus.ACTIVE
        )));
        when(service.createPickList(eq(workOrderId), any())).thenReturn(new WarehouseTaskDto(
                pickListId,
                "WT-2026-00007",
                WarehouseTaskType.PICK,
                WarehouseTaskStatus.OPEN,
                WarehouseTaskPriority.NORMAL,
                warehouseId,
                WarehouseTaskSourceType.WORK_ORDER,
                workOrderId,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null
        ));
        when(service.confirmPick(eq(workOrderId), eq(pickListId), any())).thenReturn(List.of(new RepairMaterialUsageDto(
                usageId,
                workOrderId,
                warehouseId,
                sparePartId,
                4,
                12.0
        )));
        when(service.returnMaterial(eq(workOrderId), any())).thenReturn(new WorkOrderMaterialReturnDto(
                returnId,
                workOrderId,
                usageId,
                warehouseId,
                sparePartId,
                new BigDecimal("1.0000"),
                "POSTED"
        ));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/wms-reservations", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservedById": "%s",
                                  "documentNumber": "RSV-1",
                                  "lines": [{
                                    "requirementId": "%s",
                                    "warehouseId": "%s",
                                    "sparePartId": "%s",
                                    "binId": "%s",
                                    "quantity": 4.0000
                                  }]
                                }
                                """.formatted(UUID.randomUUID(), requirementId, warehouseId, sparePartId, binId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reservationId.toString()))
                .andExpect(jsonPath("$[0].requirementId").value(requirementId.toString()));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/pick-list", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"pick\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pickListId.toString()))
                .andExpect(jsonPath("$.taskType").value("PICK"));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/pick-list/{pickListId}/confirm", workOrderId, pickListId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentNumber": "ISS-1",
                                  "issuedById": "%s",
                                  "lines": [{
                                    "requirementId": "%s",
                                    "reservationId": "%s",
                                    "warehouseId": "%s",
                                    "binId": "%s",
                                    "sparePartId": "%s",
                                    "quantity": 4.0000
                                  }]
                                }
                                """.formatted(UUID.randomUUID(), requirementId, reservationId, warehouseId, binId, sparePartId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(usageId.toString()))
                .andExpect(jsonPath("$[0].workOrderId").value(workOrderId.toString()));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/material-returns", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialUsageId": "%s",
                                  "warehouseId": "%s",
                                  "sparePartId": "%s",
                                  "binId": "%s",
                                  "stockStatus": "AVAILABLE",
                                  "quantity": 1.0000,
                                  "reason": "Unused after repair",
                                  "documentNumber": "RET-1",
                                  "strictDocumentPolicy": true,
                                  "documentGroups": [{
                                    "documentName": "Return act",
                                    "documentType": "MATERIAL_RETURN_ACT",
                                    "documentNumber": "RET-1"
                                  }]
                                }
                                """.formatted(usageId, warehouseId, sparePartId, binId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnId.toString()))
                .andExpect(jsonPath("$.status").value("POSTED"));
    }
}
