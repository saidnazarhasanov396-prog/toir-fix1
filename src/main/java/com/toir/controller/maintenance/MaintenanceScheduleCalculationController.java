package com.toir.controller.maintenance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationDto;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.enums.PlanStatus;
import com.toir.exception.RestException;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceScheduleCalculationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-schedule/calculations")
@RequiredArgsConstructor
public class MaintenanceScheduleCalculationController {

    private static final String READ_AUTH = """
            hasAnyAuthority(
              'PPR_PLAN_READ','PPR_PLAN_CREATE','PPR_PLAN_GENERATE','SYSTEM_ADMIN','*'
            )
            """;
    private static final String CREATE_AUTH =
            "hasAnyAuthority('PPR_PLAN_CREATE','PPR_PLAN_GENERATE','SYSTEM_ADMIN','*')";
    private static final String UPDATE_AUTH =
            "hasAnyAuthority('PPR_PLAN_UPDATE','SYSTEM_ADMIN','*')";
    private static final String DELETE_AUTH =
            "hasAnyAuthority('PPR_PLAN_DELETE','SYSTEM_ADMIN','*')";

    private final MaintenanceScheduleCalculationService service;
    private final ScopeAccessService scopeAccessService;

    @GetMapping
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<MaintenanceScheduleCalculationDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) PlanStatus status,
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
                status,
                PageRequest.of(page, size)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<MaintenanceScheduleCalculationDto> get(@PathVariable UUID id) {
        MaintenanceScheduleCalculationDto result = service.findById(id);
        assertCanAccessDepartment(result.plan().departmentId());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize(CREATE_AUTH)
    public ResponseEntity<MaintenanceScheduleCalculationDto> create(
            @Valid @RequestBody MaintenanceScheduleCalculationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request.withDepartmentId(scopedDepartment(request.departmentId()))));
    }

    @PutMapping("/{id}")
    @PreAuthorize(UPDATE_AUTH)
    public ResponseEntity<MaintenanceScheduleCalculationDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceScheduleCalculationRequest request
    ) {
        assertCanAccessDepartment(service.findById(id).plan().departmentId());
        return ResponseEntity.ok(
                service.update(
                        id,
                        request.withDepartmentId(scopedDepartment(request.departmentId()))
                )
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(DELETE_AUTH)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        MaintenanceScheduleCalculationDto result = service.findById(id);
        assertCanAccessDepartment(result.plan().departmentId());
        service.delete(id);
        return ResponseEntity.noContent().build();
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

    private void assertCanAccessDepartment(UUID departmentId) {
        if (departmentId == null) {
            if (!scopeAccessService.isScopeAdmin()) {
                throw new AccessDeniedException(
                        "Access denied by maintenance schedule calculation department scope");
            }
            return;
        }
        scopeAccessService.assertCanAccessDepartment(departmentId);
    }
}
