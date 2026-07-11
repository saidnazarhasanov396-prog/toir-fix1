package com.toir.controller;
import com.toir.dto.plannedshutdown.*;
import com.toir.exception.RestException;
import com.toir.enums.PlanStatus;
import com.toir.service.PlannedShutdownService;
import com.toir.util.PaginationUtils;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/planned-shutdowns")
@Tag(name = "planned-shutdowns")
@RequiredArgsConstructor
public class PlannedShutdownController {

    private final PlannedShutdownService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<Page<PlannedShutdownDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        List<PlannedShutdownDto> rows = service.findAllFiltered(departmentId, status, search);
        Comparator<PlannedShutdownDto> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "status" -> Comparator.comparing(
                    PlannedShutdownDto::status,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "startAt" -> Comparator.comparing(
                    PlannedShutdownDto::startAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "endAt" -> Comparator.comparing(
                    PlannedShutdownDto::endAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator != null) {
            if (SortUtils.direction(sortDir, Sort.Direction.DESC).isDescending()) {
                comparator = comparator.reversed();
            }
            rows = rows.stream().sorted(comparator).toList();
        }
        return ResponseEntity.ok(PaginationUtils.page(rows, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CREATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> create(@Valid @RequestBody PlannedShutdownCreateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> update(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownUpdateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @GetMapping("/{id}/assets")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownAssetScopeResponse> getAssets(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getAssets(id));
    }

    @PutMapping("/{id}/assets")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownAssetScopeResponse> replaceAssets(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownAssetReplaceRequest request) {
        return ResponseEntity.ok(service.replaceAssets(id, request));
    }

    @GetMapping("/{id}/work-items")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownWorkItemScopeResponse> listWorkItems(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listWorkItems(id));
    }

    @PostMapping("/{id}/work-items")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownWorkItemScopeResponse> addWorkItem(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownWorkItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addWorkItem(id, request));
    }

    @PutMapping("/{id}/work-items/{itemId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownWorkItemScopeResponse> updateWorkItem(
            @PathVariable UUID id, @PathVariable UUID itemId,
            @Valid @RequestBody PlannedShutdownWorkItemRequest request) {
        return ResponseEntity.ok(service.updateWorkItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/work-items/{itemId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownWorkItemScopeResponse> removeWorkItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestParam Long version) {
        return ResponseEntity.ok(service.removeWorkItem(id, itemId, version));
    }

    @PutMapping("/{id}/work-items/reorder")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownWorkItemScopeResponse> reorderWorkItems(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownWorkItemReorderRequest request) {
        return ResponseEntity.ok(service.reorderWorkItems(id, request));
    }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownReadinessScopeResponse> listReadiness(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listReadiness(id));
    }

    @PostMapping("/{id}/readiness")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownReadinessScopeResponse> addReadiness(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownReadinessItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addReadinessItem(id, request));
    }

    @PutMapping("/{id}/readiness/{itemId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownReadinessScopeResponse> updateReadiness(
            @PathVariable UUID id, @PathVariable UUID itemId,
            @Valid @RequestBody PlannedShutdownReadinessItemRequest request) {
        return ResponseEntity.ok(service.updateReadinessItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/readiness/{itemId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownReadinessScopeResponse> removeReadiness(
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestParam Long version) {
        return ResponseEntity.ok(service.removeReadinessItem(id, itemId, version));
    }

    @PostMapping("/{id}/readiness/{itemId}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_PREPARE')")
    public ResponseEntity<PlannedShutdownReadinessScopeResponse> completeReadiness(
            @PathVariable UUID id, @PathVariable UUID itemId,
            @Valid @RequestBody PlannedShutdownReadinessActionRequest request) {
        return ResponseEntity.ok(service.completeReadinessItem(id, itemId, request));
    }

    @PostMapping("/{id}/readiness/{itemId}/reopen")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_PREPARE')")
    public ResponseEntity<PlannedShutdownReadinessScopeResponse> reopenReadiness(
            @PathVariable UUID id, @PathVariable UUID itemId,
            @Valid @RequestBody PlannedShutdownReadinessActionRequest request) {
        return ResponseEntity.ok(service.reopenReadinessItem(id, itemId, request));
    }

    @GetMapping("/{id}/isolation")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> listIsolation(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listIsolation(id));
    }

    @PostMapping("/{id}/isolation")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> addIsolation(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownIsolationPointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addIsolationPoint(id, request));
    }

    @PutMapping("/{id}/isolation/{pointId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> updateIsolation(
            @PathVariable UUID id, @PathVariable UUID pointId,
            @Valid @RequestBody PlannedShutdownIsolationPointRequest request) {
        return ResponseEntity.ok(service.updateIsolationPoint(id, pointId, request));
    }

    @DeleteMapping("/{id}/isolation/{pointId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> removeIsolation(
            @PathVariable UUID id, @PathVariable UUID pointId, @RequestParam Long version) {
        return ResponseEntity.ok(service.removeIsolationPoint(id, pointId, version));
    }

    @PostMapping("/{id}/isolation/{pointId}/apply")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_PREPARE')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> applyIsolation(
            @PathVariable UUID id, @PathVariable UUID pointId,
            @Valid @RequestBody PlannedShutdownIsolationActionRequest request) {
        return ResponseEntity.ok(service.applyIsolation(id, pointId, request));
    }

    @PostMapping("/{id}/isolation/{pointId}/verify")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CONFIRM_SAFE_STATE')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> verifyIsolation(
            @PathVariable UUID id, @PathVariable UUID pointId,
            @Valid @RequestBody PlannedShutdownIsolationActionRequest request) {
        return ResponseEntity.ok(service.verifyIsolation(id, pointId, request));
    }

    @PostMapping("/{id}/isolation/{pointId}/release")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_STARTUP')")
    public ResponseEntity<PlannedShutdownIsolationScopeResponse> releaseIsolation(
            @PathVariable UUID id, @PathVariable UUID pointId,
            @Valid @RequestBody PlannedShutdownIsolationActionRequest request) {
        return ResponseEntity.ok(service.releaseIsolation(id, pointId, request));
    }

    @GetMapping("/{id}/readiness/assessment")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownReadinessAssessment> assessReadiness(@PathVariable UUID id) {
        return ResponseEntity.ok(service.assessReadiness(id, null));
    }

    @GetMapping("/{id}/safe-state/assessment")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CONFIRM_SAFE_STATE')")
    public ResponseEntity<PlannedShutdownReadinessAssessment> assessSafeState(@PathVariable UUID id) {
        return ResponseEntity.ok(service.assessSafeState(id, null));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<List<PlannedShutdownStatusHistoryResponse>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(service.history(id));
    }

    @PostMapping("/{id}/form-scope")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> formScope(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.formScope(id, request));
    }

    @PostMapping("/{id}/begin-readiness")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> beginReadiness(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.beginReadiness(id, request));
    }

    @PostMapping("/{id}/request-approval")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_REQUEST_APPROVAL')")
    public ResponseEntity<PlannedShutdownDetailResponse> requestApproval(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.requestApproval(id, request));
    }

    @PostMapping("/{id}/prepare")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_PREPARE')")
    public ResponseEntity<PlannedShutdownDetailResponse> prepare(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.prepare(id, request));
    }

    @PostMapping("/{id}/start-shutdown")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_PREPARE')")
    public ResponseEntity<PlannedShutdownDetailResponse> startShutdown(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.startShutdown(id, request));
    }

    @PostMapping("/{id}/confirm-safe-state")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CONFIRM_SAFE_STATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> confirmSafeState(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.confirmSafeState(id, request));
    }

    @PostMapping("/{id}/start-repair")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_START_REPAIR')")
    public ResponseEntity<PlannedShutdownDetailResponse> startRepair(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.startRepair(id, request));
    }

    @PostMapping("/{id}/start-testing")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_TEST')")
    public ResponseEntity<PlannedShutdownDetailResponse> startTesting(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.startTesting(id, request));
    }

    @PostMapping("/{id}/start-startup")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_STARTUP')")
    public ResponseEntity<PlannedShutdownDetailResponse> startStartup(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.startStartup(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_STARTUP')")
    public ResponseEntity<PlannedShutdownDetailResponse> complete(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CLOSE')")
    public ResponseEntity<PlannedShutdownDetailResponse> close(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.close(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CANCEL')")
    public ResponseEntity<PlannedShutdownDetailResponse> cancel(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownTransitionRequest request) {
        return ResponseEntity.ok(service.cancel(id, request));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_RESCHEDULE')")
    public ResponseEntity<PlannedShutdownDetailResponse> reschedule(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownRescheduleRequest request) {
        return ResponseEntity.ok(service.reschedule(id, request));
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_EXTEND')")
    public ResponseEntity<PlannedShutdownDetailResponse> extend(@PathVariable UUID id,
            @Valid @RequestBody PlannedShutdownExtensionRequest request) {
        return ResponseEntity.ok(service.extend(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_APPROVE')")
    public ResponseEntity<PlannedShutdownDto> reject(@PathVariable UUID id) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }
}
