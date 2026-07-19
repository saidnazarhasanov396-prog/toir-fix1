package com.toir.controller;

import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.DashboardIntegrationResponse;
import com.toir.service.AnalyticsService;
import com.toir.service.InventoryAnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "analytics-integration")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ANALYTICS_READ')")
public class DashboardIntegrationController {

    private final AnalyticsService analyticsService;
    private final InventoryAnalyticsService inventoryAnalyticsService;

    @GetMapping("/dashboard/v1")
    public DashboardIntegrationResponse dashboardV1() {
        AnalyticsOverview overview = analyticsService.overview();
        var sparePartsDemand = inventoryAnalyticsService.stockoutRisk();
        Instant generatedAt = Instant.now();
        return DashboardIntegrationResponse.fresh(
                "TOIR_GENERAL",
                "authorized-maintenance-scope",
                generatedAt,
                List.of(
                        new DashboardIntegrationResponse.SourceDataset(
                                "toir.equipment.v1", overview.reliabilitySnapshot()),
                        new DashboardIntegrationResponse.SourceDataset(
                                "toir.work-orders.v1", overview.totals()),
                        new DashboardIntegrationResponse.SourceDataset(
                                "toir.maintenance-performance.v1", overview.maintenanceKpis()),
                        new DashboardIntegrationResponse.SourceDataset(
                                "toir.downtime-events.v1", overview.downtimeByDepartment()),
                        new DashboardIntegrationResponse.SourceDataset(
                                "toir.spare-parts-demand.v1", sparePartsDemand),
                        new DashboardIntegrationResponse.SourceDataset(
                                "toir.dashboard-signals.v1", overview)
                )
        );
    }
}
