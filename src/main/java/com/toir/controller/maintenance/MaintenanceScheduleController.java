package com.toir.controller.maintenance;

import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
                scopeAccessService.enforceDepartmentScope(request.departmentId()),
                request.anchorMode()
        );
        return ResponseEntity.ok(service.preview(scopedRequest));
    }
}
