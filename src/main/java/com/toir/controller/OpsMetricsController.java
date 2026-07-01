package com.toir.controller;

import com.toir.dto.ops.OpsMetricsResponse;
import com.toir.service.OpsMetricsService;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight operational metrics snapshot for monitoring and health checks.
 * This endpoint complements the full dashboard for ops team workflows.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "ops")
@RequiredArgsConstructor
public class OpsMetricsController {

    private final OpsMetricsService opsMetricsService;


    @GetMapping("/metrics")
    public ResponseEntity<OpsMetricsResponse> metrics() {
        OpsMetricsService.OpsMetricsSnapshot snapshot = opsMetricsService.snapshot();
        return ResponseEntity.ok(new OpsMetricsResponse(snapshot.timestamp().toString(), snapshot.counts(), "UP"));
    }
}
