package com.toir.controller;

import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
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
        when(statsService.getStats(isNull()))
                .thenReturn(new SparePartsWarehouseStatsResponse(12, 4, 3, 18.5));

        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomenclature").value(12))
                .andExpect(jsonPath("$.activeReservations").value(4))
                .andExpect(jsonPath("$.lowStockItems").value(3))
                .andExpect(jsonPath("$.issuedToWork").value(18.5));

        verify(statsService).getStats(isNull());
    }

    @Test
    void getStatsWithWarehouseIdPassesFilterToService() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(statsService.getStats(eq(warehouseId)))
                .thenReturn(new SparePartsWarehouseStatsResponse(5, 1, 1, 7));

        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats")
                        .param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomenclature").value(5))
                .andExpect(jsonPath("$.activeReservations").value(1))
                .andExpect(jsonPath("$.lowStockItems").value(1))
                .andExpect(jsonPath("$.issuedToWork").value(7.0));

        verify(statsService).getStats(eq(warehouseId));
    }
}
