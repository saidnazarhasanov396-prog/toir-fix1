package com.toir.controller;

import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.dto.warehouse.IssuedToWorkRowDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.WarehouseSparePartsStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseSparePartsStatsControllerContractTest {

    @Mock
    WarehouseSparePartsStatsService statsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseSparePartsStatsController(statsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getStatsReturnsAllCards() throws Exception {
        when(statsService.getStats(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new SparePartsWarehouseStatsResponse(12, 4, 3, 18.5));

        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomenclature").value(12))
                .andExpect(jsonPath("$.activeReservations").value(4))
                .andExpect(jsonPath("$.lowStockItems").value(3))
                .andExpect(jsonPath("$.issuedToWork").value(18.5));

        verify(statsService).getStats(isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void getStatsWithWarehouseIdPassesFilterToService() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(statsService.getStats(eq(warehouseId), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new SparePartsWarehouseStatsResponse(5, 1, 1, 7));

        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats")
                        .param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomenclature").value(5))
                .andExpect(jsonPath("$.activeReservations").value(1))
                .andExpect(jsonPath("$.lowStockItems").value(1))
                .andExpect(jsonPath("$.issuedToWork").value(7.0));

        verify(statsService).getStats(eq(warehouseId), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void getIssuedToWorkForwardsAllFiltersAndReturnsStandardPage() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        IssuedToWorkRowDto row = new IssuedToWorkRowDto(
                movementId,
                LocalDate.of(2026, 7, 15),
                UUID.randomUUID(),
                "SP-1",
                "Bolt",
                new BigDecimal("2.5000"),
                "PCS",
                warehouseId,
                "Warehouse",
                UUID.randomUUID(),
                "WO-1",
                "Repair",
                "IN_PROGRESS",
                null,
                "Issuer",
                null,
                null,
                "DOC-1",
                null,
                "WORK_ORDER_MATERIAL_USAGE",
                null
        );
        when(statsService.getIssuedToWork(
                eq(warehouseId), eq("bolt"), eq(typeId), eq("SPARE_PART"), eq("PCS"), eq(1), eq(25)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 25), 26));

        mockMvc.perform(get("/api/v1/warehouses/spare-parts/issued-to-work")
                        .param("page", "1")
                        .param("size", "25")
                        .param("warehouseId", warehouseId.toString())
                        .param("search", "bolt")
                        .param("typeId", typeId.toString())
                        .param("itemType", "SPARE_PART")
                        .param("unit", "PCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movementId").value(movementId.toString()))
                .andExpect(jsonPath("$.content[0].workOrderNumber").value("WO-1"))
                .andExpect(jsonPath("$.content[0].quantity").value(2.5))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(26));
    }
}
