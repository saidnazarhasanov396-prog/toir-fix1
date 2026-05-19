package com.toir.controller;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
@Tag(name = "dashboards")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ANALYTICS_READ')")
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverview> overview(
            @RequestParam(required = false) UUID departmentId
    ) {
        return ResponseEntity.ok(service.overview(departmentId));
    }
}
