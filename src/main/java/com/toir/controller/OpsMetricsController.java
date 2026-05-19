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
 * ÐžÐ¿ÐµÑ€Ð°Ñ‚Ð¸Ð²Ð½Ñ‹Ðµ Ð¼ÐµÑ‚Ñ€Ð¸ÐºÐ¸ ÑÐ¸ÑÑ‚ÐµÐ¼Ñ‹ â€” Ð»Ñ‘Ð³ÐºÐ¸Ð¹ ÑÐ½Ð¸Ð¼Ð¾Ðº ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ñ Ð´Ð»Ñ Ð¼Ð¾Ð½Ð¸Ñ‚Ð¾Ñ€Ð¸Ð½Ð³Ð°
 * Ð¸ health-check'Ð¾Ð². ÐÐµ Ð·Ð°Ð¼ÐµÐ½ÑÐµÑ‚ full dashboard, Ð½Ð¾ ÑƒÐ´Ð¾Ð±ÐµÐ½ Ð´Ð»Ñ ops team.
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
