package com.toir.controller;
import com.toir.dto.dashboard.CockpitOverview;
import com.toir.dto.dashboard.DashboardOverview;
import com.toir.dto.dashboard.DashboardEmergencyEventDto;
import com.toir.dto.dashboard.WorkOrdersByEquipmentTypeResponse;
import com.toir.service.DashboardCockpitService;
import com.toir.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

import com.toir.dto.dashboard.EquipmentLifecycleSummaryResponse;
import com.toir.service.DashboardLifecycleService;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final DashboardLifecycleService lifecycleService;
    private final DashboardCockpitService cockpitService;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverview> overview(
            @RequestParam(required = false) UUID departmentId
    ) {
        return ResponseEntity.ok(service.overview(departmentId));
    }

    @GetMapping("/emergencies")
    public ResponseEntity<Page<DashboardEmergencyEventDto>> emergencies(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.emergencyEvents(departmentId, page, size, sourceType, search));
    }

    @GetMapping("/work-orders/by-equipment-type")
    public ResponseEntity<WorkOrdersByEquipmentTypeResponse> workOrdersByEquipmentType(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "ACTIVE") String statusScope
    ) {
        return ResponseEntity.ok(service.workOrdersByEquipmentType(departmentId, statusScope));
    }

    @GetMapping("/cockpit")
    public ResponseEntity<CockpitOverview> cockpit(
            @RequestParam(required = false) UUID departmentId
    ) {
        return ResponseEntity.ok(cockpitService.cockpit(departmentId));
    }

    @GetMapping("/equipment-lifecycle")
    public ResponseEntity<EquipmentLifecycleSummaryResponse> equipmentLifecycle() {
        return ResponseEntity.ok(lifecycleService.getLifecycleSummary());
    }
}
