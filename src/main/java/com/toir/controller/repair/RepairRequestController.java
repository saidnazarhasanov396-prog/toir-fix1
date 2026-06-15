package com.toir.controller.repair;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestClarificationRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestMeterReadingBatchRequest;
import com.toir.dto.repairrequest.RepairRequestMeterRequirementDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/repair-requests")
@Tag(name = "repair-requests")
@RequiredArgsConstructor
public class RepairRequestController {

    private final RepairRequestService service;
    private final ApprovalService approvalService;
    private final RepairRequestRepository repository;
    private final ScopeAccessService scopeAccessService;


    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<Page<RepairRequestDto>> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false)PriorityLevel priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        UUID scopedDepartmentId = resolveDepartmentFilter(departmentId, equipmentId);
        return ResponseEntity.ok(service.search(status, scopedDepartmentId, equipmentId,priority, page, size, search));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<RepairRequestStatsResponse> stats(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.getStats(
                resolveDepartmentFilter(departmentId, equipmentId),
                equipmentId,
                search
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<RepairRequestDto> get(@PathVariable UUID id) {
        assertCanReadRequest(requestOrThrow(id));
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/{id}/meter-readings/requirements")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<List<RepairRequestMeterRequirementDto>> meterRequirements(@PathVariable UUID id) {
        assertCanReadRequest(requestOrThrow(id));
        return ResponseEntity.ok(service.getMeterRequirements(id));
    }

    @PostMapping("/{id}/meter-readings")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_UPDATE') or hasAuthority('METER_READING_CREATE')")
    public ResponseEntity<List<MeterReadingDto>> addMeterReadings(
            @PathVariable UUID id,
            @Valid @RequestBody RepairRequestMeterReadingBatchRequest request
    ) {
        assertCanMutateRequest(requestOrThrow(id));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMeterReadings(id, request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_CREATE')")
    public ResponseEntity<RepairRequestDto> create(@Valid @RequestBody RepairRequestRequest request) {
        assertCanCreateRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*')")
    public ResponseEntity<RepairRequestDto> changeStatus(
            @PathVariable UUID id,
            @RequestParam RequestStatus status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String comment
    ) {
        assertCanMutateRequest(requestOrThrow(id));
        return ResponseEntity.ok(service.changeStatus(id, status, reason != null ? reason : comment));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_APPROVE')")
    public ResponseEntity<ApprovalRequestDto> approve(@PathVariable UUID id,
                                                      @RequestParam(required = false) UUID approverId) {
        RepairRequest repairRequest = requestOrThrow(id);
        assertCanMutateRequest(repairRequest);
        service.assertMeterReadingsReadyForApproval(id);
        return ResponseEntity.ok(approvalService.createOrReuseApprovalForDocument(
                "REPAIR_REQUEST",
                id,
                null,
                approverId,
                "REPAIR_REQUEST_APPROVER",
                "Repair request approval: " + repairRequest.getNumber(),
                "Approval workflow request for repair request " + repairRequest.getNumber()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_CLOSE')")
    public ResponseEntity<RepairRequestDto> close(@PathVariable UUID id, @Valid @RequestBody CloseRequestRequest request) {
        assertCanMutateRequest(requestOrThrow(id));
        return ResponseEntity.ok(service.close(id, request));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_ASSIGN')")
    public ResponseEntity<RepairRequestDto> assign(@PathVariable UUID id, @RequestParam UUID assigneeId) {
        assertCanMutateRequest(requestOrThrow(id));
        return ResponseEntity.ok(service.assign(id, assigneeId));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_REJECT')")
    public ResponseEntity<RepairRequestDto> reject(@PathVariable UUID id, @RequestParam String reason) {
        assertCanMutateRequest(requestOrThrow(id));
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }

    @PostMapping("/{id}/request-clarification")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_UPDATE')")
    public ResponseEntity<RepairRequestDto> requestClarification(
            @PathVariable UUID id,
            @RequestParam(required = false) String comment,
            @RequestBody(required = false) RepairRequestClarificationRequest request
    ) {
        assertCanMutateRequest(requestOrThrow(id));
        if (request == null) {
            return ResponseEntity.ok(service.requestClarification(id, comment));
        }
        return ResponseEntity.ok(service.requestClarification(id, request));
    }

    private UUID resolveDepartmentFilter(UUID departmentId, UUID equipmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(departmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by repair request department scope");
        }
        return scopedDepartmentId;
    }

    private RepairRequest requestOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
    }

    private void assertCanReadRequest(RepairRequest request) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (request.getDepartmentId() != null && scopeAccessService.canAccessDepartment(request.getDepartmentId())) {
            return;
        }
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        if (request.getReporterId() != null && request.getReporterId().equals(currentUserId)) {
            return;
        }
        if (request.getAssignedToId() != null && request.getAssignedToId().equals(currentUserId)) {
            return;
        }
        throw new AccessDeniedException("Access denied by repair request scope");
    }

    private void assertCanCreateRequest(RepairRequestRequest request) {
        assertCanAccessDepartmentForMutation(request.departmentId());
    }

    private void assertCanMutateRequest(RepairRequest request) {
        assertCanAccessDepartmentForMutation(request.getDepartmentId());
    }

    private void assertCanAccessDepartmentForMutation(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw new AccessDeniedException("Access denied by repair request department scope");
        }
    }
}
