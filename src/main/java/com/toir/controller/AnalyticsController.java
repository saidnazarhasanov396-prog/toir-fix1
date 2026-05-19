package com.toir.controller;
import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.dto.analytics.FailureParetoResponse;
import com.toir.dto.analytics.RcaEquipmentResponse;
import com.toir.dto.analytics.RcaOverviewResponse;
import com.toir.entity.ReliabilityMetric;
import com.toir.service.AnalyticsService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ANALYTICS_READ')")
public class AnalyticsController {

    private final AnalyticsService service;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverview> overview() {
        return ResponseEntity.ok(service.overview());
    }

    @GetMapping("/pareto/failures")
    public ResponseEntity<FailureParetoResponse> paretoFailures() {
        return ResponseEntity.ok(service.failurePareto());
    }

    @GetMapping("/rca/overview")
    public ResponseEntity<RcaOverviewResponse> rcaOverview() {
        return ResponseEntity.ok(service.rcaOverview());
    }

    @GetMapping("/rca/equipment/{equipmentId}")
    public ResponseEntity<RcaEquipmentResponse> rcaEquipment(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.rcaEquipment(equipmentId));
    }

    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<EquipmentAnalyticsResponse> equipmentAnalytics(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.equipmentAnalytics(equipmentId));
    }

    @GetMapping("/reliability")
    public ResponseEntity<Page<ReliabilityMetric>> reliabilityList(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.reliabilityList(), page, size));
    }
}
