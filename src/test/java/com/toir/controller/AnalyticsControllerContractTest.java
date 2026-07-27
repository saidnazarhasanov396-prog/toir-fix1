package com.toir.controller;

import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.service.AnalyticsService;
import com.toir.service.InventoryAnalyticsService;
import com.toir.service.ErpPresentationDatasetProvider;
import com.toir.service.ReliabilityPassportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerContractTest {

    @Mock
    AnalyticsService service;

    @Mock
    ReliabilityPassportService reliabilityPassportService;

    @Mock
    InventoryAnalyticsService inventoryAnalyticsService;

    @Mock
    ErpPresentationDatasetProvider presentationDatasets;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AnalyticsController(service, reliabilityPassportService),
                        new DashboardIntegrationController(service, inventoryAnalyticsService, presentationDatasets)
                )
                .build();
    }

    @Test
    void downtimeEventsPassesDepartmentAndPaginationToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(service.downtimeEvents(departmentId, 2, 15))
                .thenReturn(Page.empty(PageRequest.of(2, 15)));

        mockMvc.perform(get("/api/v1/analytics/downtime-events")
                        .param("departmentId", departmentId.toString())
                        .param("page", "2")
                        .param("size", "15"))
                .andExpect(status().isOk());

        verify(service).downtimeEvents(departmentId, 2, 15);
    }

    @Test
    void exposesCanonicalDashboardIntegrationContract() throws Exception {
        when(service.overview()).thenReturn(new AnalyticsOverview(
                new AnalyticsOverview.Totals(1, 2, 3, 4),
                new AnalyticsOverview.Kpis(1, 2, 3, 4, 5, 6, 7, 8),
                List.of(), List.of(), List.of(), List.of(), List.of()
        ));
        when(inventoryAnalyticsService.stockoutRisk()).thenReturn(List.of());
        when(presentationDatasets.get("toir.maintenance-page.v1"))
                .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        when(presentationDatasets.get("toir.executive-presentation.v1"))
                .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());

        mockMvc.perform(get("/api/analytics/dashboard/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.moduleCode").value("TOIR_GENERAL"))
                .andExpect(jsonPath("$.sourceSystem").value("TOIR_GENERAL"))
                .andExpect(jsonPath("$.sourceRevision").isNumber())
                .andExpect(jsonPath("$.freshness.status").value("FRESH"))
                .andExpect(jsonPath("$.datasets[0].type").value("toir.equipment.v1"))
                .andExpect(jsonPath("$.datasets[6].type").value("toir.maintenance-page.v1"))
                .andExpect(jsonPath("$.datasets[7].type").value("toir.executive-presentation.v1"));
    }
}
