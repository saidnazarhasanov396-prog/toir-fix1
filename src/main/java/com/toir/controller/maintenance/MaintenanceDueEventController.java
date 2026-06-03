package com.toir.controller.maintenance;

import com.toir.dto.maintenancedue.CancelMaintenanceDueEventRequest;
import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceDueEventService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-due-events")
@RequiredArgsConstructor
public class MaintenanceDueEventController {

    private final MaintenanceDueEventService service;
    private final MaintenanceAutomationService automationService;
    private final ScopeAccessService scopeAccessService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_EVENT_READ') or hasAuthority('PPR_PLAN_READ')")
    public ResponseEntity<Page<MaintenanceDueEventDto>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID regulationId,
            @RequestParam(required = false) MaintenanceDueEventStatus status,
            @RequestParam(required = false) MaintenanceDueStatus dueStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.search(
                equipmentId,
                scopeAccessService.enforceDepartmentScope(departmentId),
                regulationId,
                status,
                dueStatus,
                from,
                to,
                page,
                size
        ));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_EVENT_APPROVE') or hasAuthority('PPR_TASK_APPROVE')")
    public ResponseEntity<MaintenanceDueEventDto> approve(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(automationService.approveDueEvent(id, currentUserId(user)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_EVENT_CANCEL') or hasAuthority('PPR_TASK_CANCEL')")
    public ResponseEntity<MaintenanceDueEventDto> cancel(@PathVariable UUID id,
                                                         @RequestBody(required = false) CancelMaintenanceDueEventRequest request) {
        return ResponseEntity.ok(service.toDto(service.cancel(id, request == null ? null : request.reason())));
    }

    @PostMapping("/{id}/work-order")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CREATE') or hasAuthority('MAINTENANCE_EVENT_APPROVE')")
    public ResponseEntity<MaintenanceDueEventDto> createWorkOrder(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(automationService.createWorkOrderFromEvent(id, currentUserId(user)));
    }

    private UUID currentUserId(AuthenticatedUser user) {
        return user == null ? null : UUID.fromString(user.id());
    }
}
