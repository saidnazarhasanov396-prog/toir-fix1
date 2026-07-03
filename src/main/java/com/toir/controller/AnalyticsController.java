package com.toir.controller;
import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.dto.analytics.AnalyticsDowntimeEventRow;
import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.dto.analytics.FailureParetoResponse;
import com.toir.dto.analytics.RcaEquipmentResponse;
import com.toir.dto.analytics.RcaOverviewResponse;
import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.service.AnalyticsService;
import com.toir.service.ReliabilityPassportService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final ReliabilityPassportService reliabilityPassportService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverview> overview() {
        return ResponseEntity.ok(service.overview());
    }

    @GetMapping("/downtime-events")
    public ResponseEntity<Page<AnalyticsDowntimeEventRow>> downtimeEvents(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.downtimeEvents(departmentId, page, size));
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
    public ResponseEntity<Page<ReliabilityPassport>> reliabilityList(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(reliabilityPassportService.list(equipmentId, search, null, page, size));
    }
}
