package com.toir.controller.maintenance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleOption;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-schedule")
@Tag(name = "maintenance-schedule")
@RequiredArgsConstructor
public class MaintenanceScheduleController {

    private final MaintenanceScheduleService service;
    private final ScopeAccessService scopeAccessService;

    @PostMapping("/preview")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*')
            or hasAuthority('PPR_PLAN_GENERATE') or hasAuthority('PPR_PLAN_CREATE')
            """)
    public ResponseEntity<MaintenanceSchedulePreviewResponse> preview(
            @Valid @RequestBody MaintenanceSchedulePreviewRequest request
    ) {
        MaintenanceSchedulePreviewRequest scopedRequest = new MaintenanceSchedulePreviewRequest(
                request.fromDate(),
                request.toDate(),
                request.scopeType(),
                request.equipmentIds(),
                request.equipmentTypeIds(),
                scopedDepartment(request.departmentId()),
                request.anchorMode(),
                request.shiftFromExcludedWeekdays(),
                request.excludedWeekdays(),
                request.recurrenceAnchor()
        );
        return ResponseEntity.ok(service.preview(scopedRequest));
    }

    @GetMapping("/options")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*')
            or hasAuthority('PPR_PLAN_GENERATE') or hasAuthority('PPR_PLAN_CREATE')
            """)
    public ResponseEntity<Page<MaintenanceScheduleOption>> options(
            @RequestParam MaintenanceScheduleScopeType scopeType,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.options(
                scopeType,
                scopedDepartment(departmentId),
                normalizeSearch(search),
                page,
                size
        ));
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin()
                && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by maintenance schedule department scope");
        }
        return scopedDepartmentId;
    }
}
