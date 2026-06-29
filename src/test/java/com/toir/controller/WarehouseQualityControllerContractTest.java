package com.toir.controller;

import com.toir.dto.warehouse.WarehouseQualityTransferDto;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.WarehouseQualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseQualityControllerContractTest {

    @Mock
    WarehouseQualityService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseQualityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void routesReturnQualityAndWriteoffDtos() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID writeoffId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        when(service.transferStatus(any())).thenReturn(new WarehouseQualityTransferDto(
                UUID.randomUUID(),
                warehouseId,
                sparePartId,
                binId,
                WarehouseStockStatus.AVAILABLE,
                WarehouseStockStatus.QUARANTINE,
                new BigDecimal("3.0000"),
                "POSTED"
        ));
        when(service.createWriteoffRequest(any())).thenReturn(writeoff(writeoffId, warehouseId, sparePartId, binId, WarehouseWriteoffStatus.DRAFT, null));
        when(service.submitForApproval(writeoffId)).thenReturn(writeoff(writeoffId, warehouseId, sparePartId, binId, WarehouseWriteoffStatus.PENDING_APPROVAL, approvalId));
        when(service.approve(eq(writeoffId), any())).thenReturn(writeoff(writeoffId, warehouseId, sparePartId, binId, WarehouseWriteoffStatus.APPROVED, approvalId));
        when(service.post(writeoffId)).thenReturn(writeoff(writeoffId, warehouseId, sparePartId, binId, WarehouseWriteoffStatus.POSTED, approvalId));
        when(service.reject(eq(writeoffId), any())).thenReturn(writeoff(writeoffId, warehouseId, sparePartId, binId, WarehouseWriteoffStatus.REJECTED, approvalId));

        mockMvc.perform(post("/api/v1/warehouse/quality/status-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": "%s",
                                  "sparePartId": "%s",
                                  "binId": "%s",
                                  "fromStatus": "AVAILABLE",
                                  "toStatus": "QUARANTINE",
                                  "quantity": 3.0000,
                                  "reason": "quality hold",
                                  "checkedById": "%s"
                                }
                                """.formatted(warehouseId, sparePartId, binId, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toStatus").value("QUARANTINE"));

        mockMvc.perform(post("/api/v1/warehouse/writeoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeoffBody(warehouseId, sparePartId, binId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(writeoffId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/submit", writeoffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/approve", writeoffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approverId\":\"%s\",\"comment\":\"ok\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/post", writeoffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/reject", writeoffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approverId\":\"%s\",\"comment\":\"no\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private String writeoffBody(UUID warehouseId, UUID sparePartId, UUID binId) {
        return """
                {
                  "warehouseId": "%s",
                  "sparePartId": "%s",
                  "binId": "%s",
                  "stockStatus": "AVAILABLE",
                  "quantity": 2.0000,
                  "reason": "obsolete",
                  "requestedById": "%s",
                  "documentNumber": "WOFF-ACT-1"
                }
                """.formatted(warehouseId, sparePartId, binId, UUID.randomUUID());
    }

    private WarehouseWriteoffRequestDto writeoff(UUID id,
                                                 UUID warehouseId,
                                                 UUID sparePartId,
                                                 UUID binId,
                                                 WarehouseWriteoffStatus status,
                                                 UUID approvalId) {
        return new WarehouseWriteoffRequestDto(
                id,
                "WOFF-1",
                warehouseId,
                sparePartId,
                binId,
                "LOT-W",
                "SN-W",
                LocalDate.of(2028, 1, 31),
                WarehouseStockStatus.WRITEOFF_PENDING,
                new BigDecimal("2.0000"),
                "obsolete",
                status,
                UUID.randomUUID(),
                null,
                approvalId,
                null,
                "WOFF-ACT-1",
                null
        );
    }
}
