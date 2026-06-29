package com.toir.controller;

import com.toir.dto.warehouse.WarehouseBinDto;
import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.WarehouseBinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
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
class WarehouseBinControllerContractTest {

    @Mock
    WarehouseBinService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseBinController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listBinsReturnsWarehouseBins() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        when(service.list(warehouseId)).thenReturn(List.of(binDto(warehouseId, binId)));

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/bins", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(binId.toString()))
                .andExpect(jsonPath("$[0].code").value("A-01-02-03"))
                .andExpect(jsonPath("$[0].qualityZoneType").value("STORAGE"));
    }

    @Test
    void createBinReturnsCreatedDto() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        when(service.create(eq(warehouseId), any())).thenReturn(binDto(warehouseId, binId));

        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/bins", warehouseId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "A-01-02-03",
                                  "qualityZoneType": "STORAGE",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(binId.toString()))
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()));
    }

    @Test
    void stockBalancesReturnsBinScopedBalances() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        when(service.stockBalances(warehouseId, binId)).thenReturn(List.of(new WarehouseStockBalanceDto(
                balanceId,
                warehouseId,
                UUID.randomUUID(),
                binId,
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                null,
                null,
                null,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.valueOf(9),
                null,
                Instant.now()
        )));

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/bins/{binId}/stock-balances", warehouseId, binId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(balanceId.toString()))
                .andExpect(jsonPath("$[0].binId").value(binId.toString()))
                .andExpect(jsonPath("$[0].availableQty").value(9));
    }

    private WarehouseBinDto binDto(UUID warehouseId, UUID binId) {
        return new WarehouseBinDto(
                binId,
                warehouseId,
                "A-01-02-03",
                null,
                "A",
                "01",
                "02",
                "PALLET",
                null,
                null,
                WarehouseQualityZoneType.STORAGE,
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                false,
                true,
                10,
                2,
                null,
                null,
                Instant.now()
        );
    }
}
