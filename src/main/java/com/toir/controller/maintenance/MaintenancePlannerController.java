package com.toir.controller.maintenance;

import com.toir.dto.maintenanceworkspace.MaintenancePlannerBacklogItem;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerCapacityResponse;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerScheduleRequest;
import com.toir.service.maintenanceworkspace.MaintenancePlannerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/maintenance/planner")
@Tag(name = "maintenance-planner")
@RequiredArgsConstructor
public class MaintenancePlannerController {

    private final MaintenancePlannerService service;

    @GetMapping("/backlog")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<List<MaintenancePlannerBacklogItem>> backlog() {
        return ResponseEntity.ok(service.backlog());
    }

    @GetMapping("/capacity")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<MaintenancePlannerCapacityResponse> capacity() {
        return ResponseEntity.ok(service.capacity());
    }

    @GetMapping("/material-readiness")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<List<MaintenancePlannerBacklogItem>> materialReadiness() {
        return ResponseEntity.ok(service.materialReadiness());
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE')")
    public ResponseEntity<MaintenancePlannerBacklogItem> schedule(@RequestBody MaintenancePlannerScheduleRequest request) {
        return ResponseEntity.ok(service.schedule(request));
    }
}
