package com.toir.controller;

import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.service.AnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/downtime-analytics")
@Tag(name = "downtime-analytics-compat")
@RequiredArgsConstructor
public class DowntimeAnalyticsCompatibilityController {

    private final AnalyticsService analyticsService;

    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<EquipmentAnalyticsResponse> equipmentAnalytics(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(analyticsService.equipmentAnalytics(equipmentId));
    }
}

