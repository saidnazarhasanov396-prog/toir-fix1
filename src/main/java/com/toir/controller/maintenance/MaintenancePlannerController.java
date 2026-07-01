package com.toir.controller.maintenance;

import com.toir.dto.maintenanceworkspace.MaintenancePlannerBacklogItem;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerCapacityResponse;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerScheduleRequest;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.service.maintenanceworkspace.MaintenancePlannerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance/planner")
@Tag(name = "maintenance-planner")
@RequiredArgsConstructor
public class MaintenancePlannerController {

    private final MaintenancePlannerService service;

    @GetMapping("/backlog")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<List<MaintenancePlannerBacklogItem>> backlog(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String readinessStatus,
            @RequestParam(required = false) Instant scheduledFrom,
            @RequestParam(required = false) Instant scheduledTo
    ) {
        return ResponseEntity.ok(service.backlog(plannerFilter(search, departmentId, equipmentId, priority, status, readinessStatus, scheduledFrom, scheduledTo)));
    }

    @GetMapping("/capacity")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<MaintenancePlannerCapacityResponse> capacity(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String readinessStatus,
            @RequestParam(required = false) Instant scheduledFrom,
            @RequestParam(required = false) Instant scheduledTo
    ) {
        return ResponseEntity.ok(service.capacity(plannerFilter(search, departmentId, equipmentId, priority, status, readinessStatus, scheduledFrom, scheduledTo)));
    }

    @GetMapping("/material-readiness")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<List<MaintenancePlannerBacklogItem>> materialReadiness(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String readinessStatus,
            @RequestParam(required = false) Instant scheduledFrom,
            @RequestParam(required = false) Instant scheduledTo
    ) {
        return ResponseEntity.ok(service.materialReadiness(plannerFilter(search, departmentId, equipmentId, priority, status, readinessStatus, scheduledFrom, scheduledTo)));
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE')")
    public ResponseEntity<MaintenancePlannerBacklogItem> schedule(@RequestBody MaintenancePlannerScheduleRequest request) {
        return ResponseEntity.ok(service.schedule(request));
    }

    private MaintenanceWorkspaceFilter plannerFilter(
            String search,
            UUID departmentId,
            UUID equipmentId,
            String priority,
            String status,
            String readinessStatus,
            Instant scheduledFrom,
            Instant scheduledTo
    ) {
        return new MaintenanceWorkspaceFilter(search, departmentId, equipmentId, priority, status, null, null, null, readinessStatus, scheduledFrom, scheduledTo);
    }
}
