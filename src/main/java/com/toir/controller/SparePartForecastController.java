package com.toir.controller;

import com.toir.dto.sparepartforecast.SparePartForecastEvaluateResponse;
import com.toir.dto.sparepartforecast.SparePartForecastRequest;
import com.toir.dto.sparepartforecast.SparePartForecastSummaryDto;
import com.toir.service.maintanance.SparePartForecastService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spare-part-forecast")
@RequiredArgsConstructor
public class SparePartForecastController {

    private final SparePartForecastService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or (hasAuthority('STOCK_READ') and hasAuthority('MAINTENANCE_EVENT_READ'))")
    public ResponseEntity<SparePartForecastSummaryDto> forecast(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) Boolean onlyDeficit) {
        return ResponseEntity.ok(service.forecast(new SparePartForecastRequest(
                days,
                from,
                to,
                warehouseId,
                departmentId,
                equipmentId,
                templateId,
                onlyDeficit
        )));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_AUTOMATION_RUN')")
    public ResponseEntity<SparePartForecastEvaluateResponse> evaluate(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) Boolean onlyDeficit) {
        return ResponseEntity.ok(service.evaluateAndCreateIssues(new SparePartForecastRequest(
                days,
                from,
                to,
                warehouseId,
                departmentId,
                equipmentId,
                templateId,
                onlyDeficit
        )));
    }
}
