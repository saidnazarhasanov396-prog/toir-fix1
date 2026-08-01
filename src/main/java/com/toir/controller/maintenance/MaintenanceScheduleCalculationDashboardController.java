package com.toir.controller.maintenance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationDto;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationStatsResponse;
import com.toir.exception.RestException;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceScheduleCalculationDashboardService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-schedule/calculations")
@RequiredArgsConstructor
public class MaintenanceScheduleCalculationDashboardController {

    private static final String READ_AUTH =
            "hasAnyAuthority('PPR_PLAN_READ','PPR_PLAN_CREATE','PPR_PLAN_UPDATE','PPR_PLAN_DELETE',"
                    + "'PPR_PLAN_APPROVE','PPR_PLAN_GENERATE','SYSTEM_ADMIN','*')";

    private final MaintenanceScheduleCalculationDashboardService service;
    private final ScopeAccessService scopeAccessService;

    @GetMapping("/stats")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<MaintenanceScheduleCalculationStatsResponse> stats(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.ok(service.stats(scopedDepartment(departmentId), search, year));
    }

    @GetMapping("/lifecycle")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<MaintenanceScheduleCalculationDto>> listByLifecycle(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer year,
            @RequestParam String lifecycleStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw RestException.badRequest("Invalid maintenance schedule calculation pagination");
        }
        return ResponseEntity.ok(service.list(
                scopedDepartment(departmentId),
                search,
                year,
                lifecycleStatus,
                PageRequest.of(page, size)
        ));
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin()
                && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException(
                    "Access denied by maintenance schedule calculation department scope");
        }
        return scopedDepartmentId;
    }
}
