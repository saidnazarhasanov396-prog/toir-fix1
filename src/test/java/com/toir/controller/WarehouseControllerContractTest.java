package com.toir.controller;

import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.dto.warehouse.WarehouseStockLedgerDto;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import com.toir.service.warehouse.ToirWarehouseQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerContractTest {

    @Mock
    WarehouseService warehouseService;

    @Mock
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @Mock
    ToirWarehouseQueryService warehouseQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseController(
                        warehouseService,
                        warehouseEquipmentItemService,
                        warehouseQueryService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getWarehouseEquipmentReturnsPagedItems() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WarehouseEquipmentItemDto item = new WarehouseEquipmentItemDto(
                itemId,
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.AVAILABLE,
                true,
                Instant.now()
        );
        Page<WarehouseEquipmentItemDto> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(warehouseEquipmentItemService.list(
                eq(warehouseId),
                isNull(),
                eq(0),
                eq(20)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getWarehouseEquipmentSanitizesPageAndSizeAndForwardsStatusFilter() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseEquipmentItemService.list(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.OUT_OF_SERVICE),
                eq(0),
                eq(1)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        MvcResult result = mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment", warehouseId)
                        .param("status", "OUT_OF_SERVICE")
                        .param("page", "-5")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        assertThat(result.getResolvedException()).isNull();

        verify(warehouseEquipmentItemService).list(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.OUT_OF_SERVICE),
                eq(0),
                eq(1)
        );
    }

    @Test
    void getWarehouseStockBalancesReturnsPagedCoreBalances() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStockBalanceDto balance = new WarehouseStockBalanceDto(
                balanceId,
                warehouseId,
                sparePartId,
                null,
                "LOT-1",
                null,
                LocalDate.parse("2026-06-13"),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(7),
                BigDecimal.valueOf(1500),
                Instant.parse("2026-06-13T10:00:00Z")
        );
        when(warehouseQueryService.stockBalances(warehouseId, 1, 5))
                .thenReturn(new PageImpl<>(List.of(balance), PageRequest.of(1, 5), 1));

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/stock-balances", warehouseId)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(balanceId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].sparePartId").value(sparePartId.toString()))
                .andExpect(jsonPath("$.content[0].qtyOnHand").value(10))
                .andExpect(jsonPath("$.content[0].qtyReserved").value(3))
                .andExpect(jsonPath("$.content[0].availableQty").value(7));
    }

    @Test
    void getWarehouseStockLedgersReturnsPagedCoreLedgers() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStockLedgerDto ledger = new WarehouseStockLedgerDto(
                ledgerId,
                warehouseId,
                sparePartId,
                null,
                null,
                null,
                StockLedgerMovementType.RECEIPT,
                BigDecimal.valueOf(4),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(100),
                "STOCK_MOVEMENT",
                UUID.randomUUID(),
                "SM-1",
                Instant.parse("2026-06-13T10:15:00Z"),
                "receipt"
        );
        when(warehouseQueryService.stockLedgers(warehouseId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(ledger), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/stock-ledgers", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ledgerId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].sparePartId").value(sparePartId.toString()))
                .andExpect(jsonPath("$.content[0].movementType").value("RECEIPT"))
                .andExpect(jsonPath("$.content[0].quantity").value(4))
                .andExpect(jsonPath("$.content[0].referenceDocNo").value("SM-1"));
    }
}
