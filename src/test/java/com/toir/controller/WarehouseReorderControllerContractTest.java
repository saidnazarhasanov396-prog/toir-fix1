package com.toir.controller;

import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.dto.warehouse.LowStockEvaluationResultDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.LowStockRecommendationService;
import com.toir.service.WarehouseReorderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseReorderControllerContractTest {

    @Mock
    WarehouseReorderService reorderService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseReorderController(reorderService, lowStockRecommendationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSuggestionsReturnsSparePartDisplayFields() throws Exception {
        UUID stockId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ReorderSuggestionDto suggestion = new ReorderSuggestionDto(
                stockId,
                warehouseId,
                "Central Warehouse",
                sparePartId,
                "Engine Oil",
                "OIL-001",
                "LITRE",
                10.0,
                5.0,
                8.0,
                15.0,
                20.0,
                15.0,
                "CRITICAL"
        );

        when(reorderService.suggestions(isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(suggestion), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/warehouses/reorder/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stockId").value(stockId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseName").value("Central Warehouse"))
                .andExpect(jsonPath("$.content[0].sparePartId").value(sparePartId.toString()))
                .andExpect(jsonPath("$.content[0].sparePartName").value("Engine Oil"))
                .andExpect(jsonPath("$.content[0].sparePartCode").value("OIL-001"))
                .andExpect(jsonPath("$.content[0].sparePartUnit").value("LITRE"))
                .andExpect(jsonPath("$.content[0].urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(reorderService).suggestions(isNull(), eq(0), eq(20));
    }

    @Test
    void getSuggestionsWithWarehouseIdPassesIdToService() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(reorderService.suggestions(eq(warehouseId), eq(1), eq(10)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        mockMvc.perform(get("/api/v1/warehouses/reorder/suggestions")
                        .param("warehouseId", warehouseId.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(reorderService).suggestions(eq(warehouseId), eq(1), eq(10));
    }

    @Test
    void postEvaluateWithWarehouseIdReturnsEvaluationCounts() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(lowStockRecommendationService.evaluateWarehouse(warehouseId))
                .thenReturn(new LowStockEvaluationResultDto(3, 1, 1, 1, 0));

        mockMvc.perform(post("/api/v1/warehouses/reorder/evaluate")
                        .param("warehouseId", warehouseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedCount").value(3))
                .andExpect(jsonPath("$.openedCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.resolvedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0));

        verify(lowStockRecommendationService).evaluateWarehouse(warehouseId);
    }

    @Test
    void postEvaluateWithoutWarehouseIdEvaluatesAllStocks() throws Exception {
        when(lowStockRecommendationService.evaluateAll())
                .thenReturn(new LowStockEvaluationResultDto(2, 1, 0, 0, 1));

        mockMvc.perform(post("/api/v1/warehouses/reorder/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(1));

        verify(lowStockRecommendationService).evaluateAll();
    }
}
