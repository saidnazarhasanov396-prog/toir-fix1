package com.toir.controller;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.service.DashboardService;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboards")
@Tag(name = "dashboards")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public DashboardOverview overview(
            @RequestParam(required = false) UUID departmentId
    ) {
        return service.overview(departmentId);
    }
}
