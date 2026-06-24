package com.toir.controller;

import com.toir.dto.warehouseanalytics.WarehouseAbcXyzCellDto;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsKpiDto;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsOverviewDto;
import com.toir.dto.warehouseanalytics.WarehouseDeficitRowDto;
import com.toir.dto.warehouseanalytics.WarehouseDistributionRowDto;
import com.toir.dto.warehouseanalytics.WarehouseMovementPointDto;
import com.toir.dto.warehouseanalytics.WarehouseReservationRowDto;
import com.toir.dto.warehouseanalytics.WarehouseRiskDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.WarehouseAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseAnalyticsControllerContractTest {

    @Mock
    WarehouseAnalyticsService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseAnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void overviewReturnsDashboardBlocksForMockupStylePage() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();

        WarehouseAnalyticsOverviewDto response = new WarehouseAnalyticsOverviewDto(
                List.of(new WarehouseAnalyticsKpiDto(
                        "totalItems",
                        "Всего позиций",
                        2916,
                        "номенклатур",
                        34.0,
                        1.2,
                        "info",
                        "Активная номенклатура на 5 складах"
                )),
                List.of(new WarehouseMovementPointDto(LocalDate.of(2026, 6, 1), 184720, 1200, 820, 32380)),
                List.of(new WarehouseRiskDto(
                        "CRITICAL",
                        "Риск остановки компрессора К-101",
                        "Подшипник 6205 2RS — остаток 4 при минимуме 24",
                        "CREATE_PROCUREMENT",
                        sparePartId,
                        "Создать заявку"
                )),
                List.of(new WarehouseDistributionRowDto(
                        warehouseId,
                        "Центральный склад",
                        1284,
                        86420,
                        14200,
                        72220,
                        74.0,
                        6,
                        "NORMAL"
                )),
                List.of(new WarehouseDeficitRowDto(
                        sparePartId,
                        "BRG-6205-2RS",
                        "Подшипник 6205 2RS",
                        "CRITICAL",
                        warehouseId,
                        "Цех аммиака",
                        4,
                        24,
                        20,
                        20,
                        "Компрессор К-101",
                        UUID.randomUUID()
                )),
                List.of(),
                List.of(new WarehouseReservationRowDto(
                        UUID.randomUUID(),
                        sparePartId,
                        "Подшипник NU320",
                        warehouseId,
                        "Центральный склад",
                        workOrderId,
                        "WO-2026-0412",
                        "Компрессор К-101",
                        2,
                        LocalDate.of(2026, 6, 21),
                        "IN_WORK"
                )),
                List.of(new WarehouseAbcXyzCellDto("A", "X", 16, 1250000, 2))
        );
        when(service.overview(argThat(filter ->
                "MONTH".equals(filter.period())
                        && warehouseId.equals(filter.warehouseId())
                        && Boolean.TRUE.equals(filter.onlyDeficit())
        ))).thenReturn(response);

        mockMvc.perform(get("/api/v1/warehouse/analytics/overview")
                        .param("period", "MONTH")
                        .param("warehouseId", warehouseId.toString())
                        .param("onlyDeficit", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].key").value("totalItems"))
                .andExpect(jsonPath("$.movements[0].stock").value(184720.0))
                .andExpect(jsonPath("$.risks[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.warehouseDistribution[0].warehouseName").value("Центральный склад"))
                .andExpect(jsonPath("$.deficits[0].code").value("BRG-6205-2RS"))
                .andExpect(jsonPath("$.reservations[0].workOrderNumber").value("WO-2026-0412"))
                .andExpect(jsonPath("$.abcXyz[0].abcClass").value("A"));

        verify(service).overview(argThat(filter ->
                "MONTH".equals(filter.period())
                        && warehouseId.equals(filter.warehouseId())
                        && Boolean.TRUE.equals(filter.onlyDeficit())
        ));
    }
}
