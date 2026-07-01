package com.toir.controller;

import com.toir.dto.inventorycount.InventoryCountLineDto;
import com.toir.dto.inventorycount.InventoryCountSessionDto;
import com.toir.enums.InventoryCountLineStatus;
import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.InventoryCountSessionService;
import com.toir.service.warehouse.WmsOperationsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
class InventoryCountSessionControllerContractTest {

    @Mock
    InventoryCountSessionService service;

    @Mock
    WmsOperationsQueryService operationsQueryService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryCountSessionController(service, operationsQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void routesReturnInventoryCountSessionDtos() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        InventoryCountSessionDto draft = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.DRAFT);
        InventoryCountSessionDto open = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.OPEN);
        InventoryCountSessionDto counting = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.COUNTING);
        InventoryCountSessionDto review = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.REVIEW);
        InventoryCountSessionDto approved = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.APPROVED);
        InventoryCountSessionDto posted = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.POSTED);
        InventoryCountSessionDto cancelled = dto(sessionId, lineId, warehouseId, binId, sparePartId, InventoryCountSessionStatus.CANCELLED);

        when(service.create(any())).thenReturn(draft);
        when(service.findAll(eq(warehouseId), eq(InventoryCountSessionStatus.DRAFT), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(draft), PageRequest.of(0, 20), 1));
        when(service.findById(sessionId)).thenReturn(draft);
        when(service.open(sessionId)).thenReturn(open);
        when(service.countLine(eq(sessionId), eq(lineId), any())).thenReturn(counting);
        when(service.review(eq(sessionId), any())).thenReturn(review);
        when(service.approve(sessionId)).thenReturn(approved);
        when(service.postAdjustments(sessionId)).thenReturn(posted);
        when(service.cancel(eq(sessionId), eq("cancelled by supervisor"))).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/inventory/count-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": "%s",
                                  "scopeType": "WAREHOUSE",
                                  "blindCount": false,
                                  "documentNumber": "CNT-1"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/inventory/count-sessions")
                        .param("warehouseId", warehouseId.toString())
                        .param("status", "DRAFT")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(sessionId.toString()));

        mockMvc.perform(get("/api/v1/inventory/count-sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].id").value(lineId.toString()));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/open", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/lines/{lineId}/count", sessionId, lineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "countedQty": 8.0000,
                                  "countedById": "%s",
                                  "varianceReason": "short"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTING"));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/review", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strictDocumentPolicy": true,
                                  "comment": "review",
                                  "documentGroups": [{
                                    "documentName": "Variance act",
                                    "documentType": "VARIANCE_ACT"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW"));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/approve", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/post-adjustments", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/cancel", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cancelled by supervisor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private InventoryCountSessionDto dto(UUID sessionId,
                                         UUID lineId,
                                         UUID warehouseId,
                                         UUID binId,
                                         UUID sparePartId,
                                         InventoryCountSessionStatus status) {
        return new InventoryCountSessionDto(
                sessionId,
                "IC-2026-00001",
                warehouseId,
                status,
                InventoryCountScopeType.WAREHOUSE,
                null,
                null,
                null,
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                "CNT-1",
                null,
                List.of(new InventoryCountLineDto(
                        lineId,
                        warehouseId,
                        binId,
                        sparePartId,
                        "LOT-1",
                        "SN-1",
                        LocalDate.of(2028, 1, 31),
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("10.0000"),
                        new BigDecimal("8.0000"),
                        new BigDecimal("-2.0000"),
                        null,
                        InventoryCountLineStatus.COUNTED,
                        UUID.randomUUID(),
                        Instant.now(),
                        "short"
                )),
                Instant.now(),
                Instant.now()
        );
    }
}
