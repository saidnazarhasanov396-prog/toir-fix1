package com.toir.controller.repair;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestClarificationRequest;
import com.toir.dto.repairrequest.RepairRequestCloseReadinessDto;
import com.toir.dto.repairrequest.RepairRequestCostsSummaryDto;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestFilterRequest;
import com.toir.dto.repairrequest.RepairRequestMeterReadingBatchRequest;
import com.toir.dto.repairrequest.RepairRequestMeterRequirementDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.dto.repairrequest.RepairRequestTimelineEventDto;
import com.toir.dto.repairrequest.WarrantyDecisionRequest;
import com.toir.dto.repairrequest.WarrantyPreviewResponse;
import com.toir.dto.repairrequest.WarrantyStatusResponse;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.repair.RepairRequestService;
import com.toir.service.repair.RepairRequestInsightsService;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "status", "status",
            "severity", "criticality",
            "criticality", "criticality",
            "priority", "priority",
            "detectedAt", "detectedAt",
            "createdAt", "createdAt"
    );

    private final RepairRequestService service;
    private final RepairRequestInsightsService insightsService;
    private final RepairRequestRepository repository;
    private final ScopeAccessService scopeAccessService;


    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<Page<RepairRequestDto>> list(
            @ModelAttribute RepairRequestFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir
    ) {
        UUID scopedDepartmentId = resolveDepartmentFilter(filter.departmentId(), filter.equipmentId());
        RepairRequestFilterRequest scopedFilter = filter.withDepartmentId(scopedDepartmentId);
        if (sortBy == null || sortBy.isBlank()) {
            return ResponseEntity.ok(service.search(scopedFilter, page, size));
        }
        Sort sort = SortUtils.sort(sortBy, sortDir, SORT_FIELDS, "updatedAt", Sort.Direction.DESC);
        return ResponseEntity.ok(service.search(scopedFilter, page, size, sort));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<RepairRequestStatsResponse> stats(
            @ModelAttribute RepairRequestFilterRequest filter
    ) {
        UUID scopedDepartmentId = resolveDepartmentFilter(filter.departmentId(), filter.equipmentId());
        return ResponseEntity.ok(service.getStats(filter.withDepartmentId(scopedDepartmentId)));
    }

    @GetMapping("/warranty-preview")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_CREATE') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<WarrantyPreviewResponse> warrantyPreview(@RequestParam UUID equipmentId) {
        assertCanPreviewWarranty(equipmentId);
        return ResponseEntity.ok(service.getWarrantyPreview(equipmentId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<RepairRequestDto> get(@PathVariable UUID id) {
        assertCanReadRequest(requestOrThrow(id));
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/{id}/close-readiness")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<RepairRequestCloseReadinessDto> closeReadiness(@PathVariable UUID id) {
        RepairRequest request = requestOrThrow(id);
        assertCanReadRequest(request);
        return ResponseEntity.ok(insightsService.getCloseReadiness(request));
    }

    @GetMapping("/{id}/costs-summary")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<RepairRequestCostsSummaryDto> costsSummary(@PathVariable UUID id) {
        RepairRequest request = requestOrThrow(id);
        assertCanReadRequest(request);
        return ResponseEntity.ok(insightsService.getCostsSummary(request));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<List<RepairRequestTimelineEventDto>> timeline(@PathVariable UUID id) {
        RepairRequest request = requestOrThrow(id);
        assertCanReadRequest(request);
        return ResponseEntity.ok(insightsService.getTimeline(request));
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

    @GetMapping("/{id}/warranty-status")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
    public ResponseEntity<WarrantyStatusResponse> getWarrantyStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getWarrantyStatus(id));
    }

    @PostMapping("/{id}/warranty-decision")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_WARRANTY_DECISION')")
    public ResponseEntity<RepairRequestDto> recordWarrantyDecision(
            @PathVariable UUID id,
            @Valid @RequestBody WarrantyDecisionRequest request
    ) {
        assertCanMutateRequest(requestOrThrow(id));
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        return ResponseEntity.ok(service.recordWarrantyDecision(id, request, currentUserId));
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
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        UUID departmentId = request.departmentId() != null
                ? request.departmentId()
                : service.resolveDepartmentIdForCreate(request);
        assertCanAccessDepartmentForMutation(departmentId);
    }

    private void assertCanPreviewWarranty(UUID equipmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        assertCanAccessDepartmentForMutation(service.resolveDepartmentIdForEquipment(equipmentId));
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
