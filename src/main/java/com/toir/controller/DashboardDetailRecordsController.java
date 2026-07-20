package com.toir.controller;

import com.toir.dto.analytics.DashboardDetailResponse;
import com.toir.service.DashboardDetailIntegrationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/integration/dashboard/v1")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ANALYTICS_READ')")
public class DashboardDetailRecordsController {
    private final DashboardDetailIntegrationService service;

    @GetMapping("/records")
    public DashboardDetailResponse records(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limit
    ) {
        return service.records(cursor, limit);
    }
}
