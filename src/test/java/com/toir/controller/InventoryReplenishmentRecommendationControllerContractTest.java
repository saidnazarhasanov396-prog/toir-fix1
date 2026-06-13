package com.toir.controller;

import com.toir.dto.warehouse.InventoryReplenishmentReason;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.enums.NotificationSeverity;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.InventoryReplenishmentRecommendationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReplenishmentRecommendationControllerContractTest {

    @Mock
    InventoryReplenishmentRecommendationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryReplenishmentRecommendationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getRecommendationsReturnsPaginatedUnifiedRowsAndPassesFilters() throws Exception {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant from = Instant.parse("2026-06-05T00:00:00Z");
        Instant to = Instant.parse("2026-06-20T00:00:00Z");
        Instant firstDueAt = Instant.parse("2026-06-10T09:00:00Z");
        InventoryReplenishmentRecommendationDto dto = new InventoryReplenishmentRecommendationDto(
                sparePartId,
                "BRG-001",
                "Bearing",
                warehouseId,
                "Central warehouse",
                10.0,
                4.0,
                6.0,
                5.0,
                8.0,
                20.0,
                7.0,
                1.0,
                -1.0,
                9.0,
                20.0,
                UUID.randomUUID(),
                "Best Supplier",
                LocalDate.of(2026, 6, 20),
                NotificationSeverity.WARNING,
                InventoryReplenishmentReason.LOW_STOCK_AND_MAINTENANCE_FORECAST,
                1,
                firstDueAt,
                List.of()
        );

        when(service.recommendations(eq(15), eq(from), eq(to), eq(warehouseId), eq(true), eq(1), eq(10)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/v1/warehouse/replenishment-recommendations")
                        .param("days", "15")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("warehouseId", warehouseId.toString())
                        .param("onlyDeficit", "true")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sparePartId").value(sparePartId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].maintenanceDemandQty").value(7.0))
                .andExpect(jsonPath("$.content[0].totalShortageQty").value(9.0))
                .andExpect(jsonPath("$.content[0].suggestedOrderQty").value(20.0))
                .andExpect(jsonPath("$.content[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.content[0].reason").value("LOW_STOCK_AND_MAINTENANCE_FORECAST"))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(service).recommendations(eq(15), eq(from), eq(to), eq(warehouseId), eq(true), eq(1), eq(10));
    }
}
