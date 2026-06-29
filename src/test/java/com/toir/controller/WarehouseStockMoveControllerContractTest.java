package com.toir.controller;

import com.toir.dto.warehouse.WarehouseStockMoveResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.WarehouseStockMoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseStockMoveControllerContractTest {

    @Mock
    WarehouseStockMoveService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseStockMoveController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void moveReturnsAtomicMoveResponse() throws Exception {
        UUID movementId = UUID.randomUUID();
        UUID outLedgerId = UUID.randomUUID();
        UUID inLedgerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID toBinId = UUID.randomUUID();
        when(service.move(any())).thenReturn(new WarehouseStockMoveResponse(
                movementId,
                outLedgerId,
                inLedgerId,
                warehouseId,
                sparePartId,
                fromBinId,
                toBinId,
                new BigDecimal("5.0000")
        ));

        mockMvc.perform(post("/api/v1/warehouse/stock-moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": "%s",
                                  "sparePartId": "%s",
                                  "fromBinId": "%s",
                                  "toBinId": "%s",
                                  "quantity": 5.0000,
                                  "lotNumber": "LOT-7",
                                  "serialNumber": "SN-8",
                                  "expiryDate": "2027-03-15",
                                  "stockStatus": "AVAILABLE",
                                  "documentNumber": "MOVE-1",
                                  "comment": "relocation"
                                }
                                """.formatted(warehouseId, sparePartId, fromBinId, toBinId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockMovementId").value(movementId.toString()))
                .andExpect(jsonPath("$.moveOutLedgerId").value(outLedgerId.toString()))
                .andExpect(jsonPath("$.moveInLedgerId").value(inLedgerId.toString()))
                .andExpect(jsonPath("$.fromBinId").value(fromBinId.toString()))
                .andExpect(jsonPath("$.toBinId").value(toBinId.toString()))
                .andExpect(jsonPath("$.quantity").value(5.0000));
    }

    @Test
    void moveRequiresPositiveQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/warehouse/stock-moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": "%s",
                                  "sparePartId": "%s",
                                  "fromBinId": "%s",
                                  "toBinId": "%s",
                                  "quantity": 0
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}
