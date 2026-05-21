package com.toir.controller;

import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.dto.workorder.WorkOrderStatsResponse;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.WorkOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-orders")
@Tag(name = "work-orders")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService service;
    private final ApprovalService approvalService;
    private final WorkOrderRepository repository;
    private final ScopeAccessService scopeAccessService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<Page<WorkOrderDto>> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity
                .ok(service.search(status, scopedDepartment(departmentId), equipmentId, page, size, search));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<WorkOrderStatsResponse> stats(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.getStats(status, scopedDepartment(departmentId), equipmentId, search));
    }

    @GetMapping("/mobile-feed")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<Page<WorkOrderDto>> mobileFeed(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(service.mobileFeed(scopedDepartment(departmentId), equipmentId, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<WorkOrderDto> get(@PathVariable UUID id) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CREATE')")
    public ResponseEntity<WorkOrderDto> create(@Valid @RequestBody WorkOrderRequest request) {
        assertCanAccessDepartmentForMutation(request.departmentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_APPROVE')")
    public ResponseEntity<WorkOrderDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        WorkOrder workOrder = workOrderOrThrow(id);
        assertCanAccessWorkOrder(workOrder);
        if (workOrder.getStatus() != WorkOrderStatus.DRAFT && workOrder.getStatus() != WorkOrderStatus.PLANNED) {
            throw RestException.badRequest("Only DRAFT/PLANNED work orders can be approved");
        }
        approvalService.createOrReuseApprovalForDocument(
                "WORK_ORDER",
                id,
                approverId,
                approverId,
                "WORK_ORDER_APPROVER",
                "Work order approval: " + workOrder.getNumber(),
                "Approval workflow request for work order " + workOrder.getNumber()
        );
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_START')")
    public ResponseEntity<WorkOrderDto> start(@PathVariable UUID id) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.start(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_COMPLETE')")
    public ResponseEntity<WorkOrderDto> complete(@PathVariable UUID id,
            @Valid @RequestBody CompleteWorkOrderRequest request) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CLOSE')")
    public ResponseEntity<WorkOrderDto> close(@PathVariable UUID id,
            @Valid @RequestBody CloseWorkOrderRequest request) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.close(id, request));
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
        return scopedDepartmentId;
    }

    private WorkOrder workOrderOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private void assertCanAccessWorkOrder(WorkOrder workOrder) {
        assertCanAccessDepartmentForMutation(workOrder.getDepartmentId());
    }

    private void assertCanAccessDepartmentForMutation(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
    }
}
