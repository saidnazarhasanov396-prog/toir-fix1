package com.toir.controller.equipment;
import com.toir.dto.equipment.*;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService service;
    private final EquipmentRepository repository;
    private final ScopeAccessService scopeAccessService;
    private final EquipmentStatusLifecycleService statusLifecycleService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "false") boolean availableForReplacement,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, size);
        return ResponseEntity.ok(service.search(
                scopedDepartment(departmentId),
                equipmentTypeId,
                status,
                category,
                warehouseId,
                availableForReplacement,
                search,
                safePage,
                safePageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<EquipmentDetailDto> get(@PathVariable UUID id) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.findDetailById(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public EquipmentStatsResponse getEquipmentStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId
    ) {
        UUID scopedDepartmentId = scopedDepartment(departmentId);

        return service.getEquipmentStats(
                search,
                category,
                scopedDepartmentId,
                equipmentTypeId
        );
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentDto>> children(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.findChildren(id, Math.max(0, page), Math.max(1, size)));
    }

    @PostMapping
    @Operation(
            summary = "Create equipment",
            description = "At least one of departmentId or warehouseId is required. " +
                    "If warehouseId is provided, the created equipment is assigned in warehouse equipment as AVAILABLE. " +
                    "Equipment code is system-generated and must not be provided by client."
    )
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_CREATE')")
    public ResponseEntity<EquipmentDto> create(@Valid @RequestBody EquipmentCreateRequest request) {
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentUpdateRequest request) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentStatusHistoryResponse> updateStatus(@PathVariable UUID id,
                                                                       @Valid @RequestBody EquipmentStatusChangeRequest request) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(statusLifecycleService.changeStatusManually(
                id,
                request,
                scopeAccessService.currentUserIdOrNull()
        ));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentStatusHistoryResponse>> statusHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(statusLifecycleService.getHistory(
                id,
                PageRequest.of(Math.max(0, page), Math.max(1, size))
        ));
    }

    @PatchMapping("/{id}/placement")
    @Operation(
            summary = "Move equipment between warehouse and department",
            description = "Preferred frontend endpoint for equipment placement movement. " +
                    "Use targetType=WAREHOUSE to move equipment into warehouse inventory, " +
                    "or targetType=DEPARTMENT to install into a department."
    )
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TRANSFER')")
    public ResponseEntity<EquipmentDto> updatePlacement(@PathVariable UUID id,
                                                         @Valid @RequestBody EquipmentPlacementRequest request) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.ok(service.updatePlacement(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by equipment department scope");
        }
        return scopedDepartmentId;
    }

    private Equipment equipmentOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    private void assertCanAccessEquipment(Equipment equipment) {
        if (equipment.getDepartmentId() == null) {
            if (!scopeAccessService.isScopeAdmin()) {
                throw new AccessDeniedException("Access denied by equipment department scope");
            }
            return;
        }
        scopeAccessService.assertCanAccessDepartment(equipment.getDepartmentId());
    }
}
