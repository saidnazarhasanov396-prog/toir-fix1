package com.toir.controller.maintenance;

import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionRequest;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionResponse;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherQueues;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherSummary;
import com.toir.service.maintenanceworkspace.MaintenanceDispatcherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance/dispatcher")
@Tag(name = "maintenance-dispatcher")
@RequiredArgsConstructor
public class MaintenanceDispatcherController {

    private final MaintenanceDispatcherService service;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<MaintenanceDispatcherSummary> summary() {
        return ResponseEntity.ok(service.summary());
    }

    @GetMapping("/queues")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<MaintenanceDispatcherQueues> queues() {
        return ResponseEntity.ok(service.queues());
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE') or hasAuthority('REPAIR_REQUEST_UPDATE')")
    public ResponseEntity<MaintenanceDispatcherActionResponse> assign(@RequestBody MaintenanceDispatcherActionRequest request) {
        return ResponseEntity.ok(service.assign(request));
    }

    @PostMapping("/escalate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE') or hasAuthority('REPAIR_REQUEST_UPDATE')")
    public ResponseEntity<MaintenanceDispatcherActionResponse> escalate(@RequestBody MaintenanceDispatcherActionRequest request) {
        return ResponseEntity.ok(service.escalate(request));
    }
}
