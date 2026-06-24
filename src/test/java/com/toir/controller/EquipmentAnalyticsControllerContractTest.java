package com.toir.controller;

import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.AnalyticsService;
import com.toir.service.ReliabilityPassportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentAnalyticsControllerContractTest {

    @Mock
    AnalyticsService service;

    @Mock
    ReliabilityPassportService reliabilityPassportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AnalyticsController(service, reliabilityPassportService),
                        new DowntimeAnalyticsCompatibilityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void analyticsForEquipmentWithNoDataReturnsZerosAndEmptyLists() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.equipmentAnalytics(equipmentId)).thenReturn(
                new EquipmentAnalyticsResponse(
                        equipmentId.toString(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        mockMvc.perform(get("/api/v1/analytics/equipment/{equipmentId}", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").value(0))
                .andExpect(jsonPath("$.defectCount").value(0))
                .andExpect(jsonPath("$.workOrderCount").value(0))
                .andExpect(jsonPath("$.downtimeHours").value(0))
                .andExpect(jsonPath("$.totalCost").value(0))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history.length()").value(0))
                .andExpect(jsonPath("$.downtimes").isArray())
                .andExpect(jsonPath("$.downtimes.length()").value(0))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(0));
    }

    @Test
    void downtimeAnalyticsCompatibilityRouteReturns200() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.equipmentAnalytics(equipmentId)).thenReturn(
                new EquipmentAnalyticsResponse(
                        equipmentId.toString(),
                        4,
                        2,
                        3,
                        5.5,
                        230.75,
                        12.5,
                        3.2,
                        97.7,
                        0,
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        mockMvc.perform(get("/api/v1/downtime-analytics/equipment/{equipmentId}", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.requestCount").value(4))
                .andExpect(jsonPath("$.defectCount").value(2))
                .andExpect(jsonPath("$.workOrderCount").value(3))
                .andExpect(jsonPath("$.downtimeHours").value(5.5))
                .andExpect(jsonPath("$.totalCost").value(230.75))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.downtimes").isArray())
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    void analyticsForUnknownEquipmentReturns404() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.equipmentAnalytics(equipmentId))
                .thenThrow(RestException.notFound("Equipment not found: " + equipmentId));

        mockMvc.perform(get("/api/v1/analytics/equipment/{equipmentId}", equipmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/equipment/{equipmentId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
