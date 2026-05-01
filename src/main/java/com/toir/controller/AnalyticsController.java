package com.toir.controller;
import com.toir.service.AnalyticsService;

import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.dto.analytics.FailureParetoResponse;
import com.toir.dto.analytics.RcaEquipmentResponse;
import com.toir.dto.analytics.RcaOverviewResponse;
import com.toir.entity.ReliabilityMetric;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public AnalyticsOverview overview() {
        return service.overview();
    }

    @GetMapping("/pareto/failures")
    public FailureParetoResponse paretoFailures() {
        return service.failurePareto();
    }

    @GetMapping("/rca/overview")
    public RcaOverviewResponse rcaOverview() {
        return service.rcaOverview();
    }

    @GetMapping("/rca/equipment/{equipmentId}")
    public RcaEquipmentResponse rcaEquipment(@PathVariable UUID equipmentId) {
        return service.rcaEquipment(equipmentId);
    }

    @GetMapping("/equipment/{equipmentId}")
    public EquipmentAnalyticsResponse equipmentAnalytics(@PathVariable UUID equipmentId) {
        return service.equipmentAnalytics(equipmentId);
    }

    @GetMapping("/reliability")
    public List<ReliabilityMetric> reliabilityList() {
        return service.reliabilityList();
    }
}
