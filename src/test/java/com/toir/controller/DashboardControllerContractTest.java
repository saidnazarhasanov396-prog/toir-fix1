package com.toir.controller;

import com.toir.dto.dashboard.CockpitOverview;
import com.toir.dto.dashboard.DashboardEmergencyEventDto;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.DashboardCockpitService;
import com.toir.service.DashboardLifecycleService;
import com.toir.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerContractTest {

    @Mock
    DashboardService service;

    @Mock
    DashboardLifecycleService lifecycleService;

    @Mock
    DashboardCockpitService cockpitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(service, lifecycleService, cockpitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void overviewReturnsKpiRatioFields() throws Exception {
        DashboardOverview overview = new DashboardOverview(
                new DashboardOverview.Counters(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                        0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        0, 0, 0, 0),
                new DashboardOverview.PlanFact(0, 0, 0),
                new DashboardOverview.Kpis(
                        0, 0, 9.0, 0, 0, 0, 0, 0, 33.333, 33.333,
                        new DashboardOverview.Ratio(9, 100),
                        new DashboardOverview.Ratio(1, 3),
                        new DashboardOverview.Ratio(1, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new DashboardOverview.MaintenanceDueCounts(0, 0, 0, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(service.overview(null)).thenReturn(overview);

        mockMvc.perform(get("/api/v1/dashboards/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.unplannedRepairRatio.numerator").value(9))
                .andExpect(jsonPath("$.kpis.unplannedRepairRatio.denominator").value(100))
                .andExpect(jsonPath("$.kpis.pprCompletionRatio.numerator").value(1))
                .andExpect(jsonPath("$.kpis.pprCompletionRatio.denominator").value(3))
                .andExpect(jsonPath("$.kpis.overdueWorkRatio.numerator").value(1))
                .andExpect(jsonPath("$.kpis.overdueWorkRatio.denominator").value(3));
    }

    @Test
    void emergencyEventsReturnsPagedRows() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        DashboardEmergencyEventDto row = new DashboardEmergencyEventDto(
                "rr:" + sourceId,
                "REPAIR_REQUEST",
                sourceId,
                sourceId,
                null,
                null,
                "RR-1",
                "Emergency request",
                "CLOSED",
                "EMERGENCY",
                departmentId,
                UUID.randomUUID(),
                Instant.parse("2026-07-03T05:00:00Z"),
                "/repair-requests/" + sourceId
        );
        when(service.emergencyEvents(eq(departmentId), eq(0), eq(10), eq("REPAIR_REQUEST"), eq("RR-1")))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/dashboards/emergencies")
                        .param("departmentId", departmentId.toString())
                        .param("page", "0")
                        .param("size", "10")
                        .param("sourceType", "REPAIR_REQUEST")
                        .param("search", "RR-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventKey").value("rr:" + sourceId))
                .andExpect(jsonPath("$.content[0].sourceType").value("REPAIR_REQUEST"))
                .andExpect(jsonPath("$.content[0].detailPath").value("/repair-requests/" + sourceId));
    }

    @Test
    void cockpitReturnsManagementOverview() throws Exception {
        UUID departmentId = UUID.randomUUID();
        CockpitOverview overview = new CockpitOverview(
                Instant.parse("2026-07-09T03:00:00Z"),
                departmentId,
                new CockpitOverview.HealthSummary(
                        "WARNING",
                        new CockpitOverview.DomainStatus("WARNING", List.of("emergency_active")),
                        new CockpitOverview.DomainStatus("OK", List.of()),
                        new CockpitOverview.DomainStatus("OK", List.of()),
                        new CockpitOverview.DomainStatus("OK", List.of())),
                new CockpitOverview.OperationsBlock(
                        2, 7, 1, 4, 1, 3, 12.5, 80.0, 5.0, 91.0, 14.0, 2.0),
                new CockpitOverview.EquipmentBlock(
                        120, 8, 2, 93.3,
                        List.of(new CockpitOverview.StatusCount("ACTIVE", 110))),
                new CockpitOverview.WorkOrdersBlock(
                        15, 2,
                        List.of(new CockpitOverview.StatusCount("IN_PROGRESS", 9)),
                        List.of(new CockpitOverview.StatusCount("EMERGENCY", 2))),
                new CockpitOverview.FinanceBlock(
                        1000.0, 250.0, 750.0, 25.0, 3, 1, 0, 4, 1, 2, BigDecimal.TEN),
                new CockpitOverview.WarehouseBlock(
                        BigDecimal.valueOf(5000), 6, 2, 4, 1, 0),
                new CockpitOverview.PeopleBlock(1, 2, 3),
                new CockpitOverview.TrendsBlock(
                        List.of(new CockpitOverview.MonthPoint("2026-07", 5, 3, 8.5)))
        );
        when(cockpitService.cockpit(eq(departmentId))).thenReturn(overview);

        mockMvc.perform(get("/api/v1/dashboards/cockpit")
                        .param("departmentId", departmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.health.overallStatus").value("WARNING"))
                .andExpect(jsonPath("$.operations.activeEmergencies").value(2))
                .andExpect(jsonPath("$.equipment.statusDistribution[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.trends.months[0].month").value("2026-07"));
    }
}
