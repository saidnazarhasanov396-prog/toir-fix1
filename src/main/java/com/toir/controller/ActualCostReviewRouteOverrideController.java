package com.toir.controller;

import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideRegistrySummary;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.financialreview.BulkRouteOverrideApplyResponse;
import com.toir.dto.financialreview.BulkRouteOverrideClearResponse;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ActualCostReviewRouteOverrideService;
import com.toir.util.PaginationUtils;
import com.toir.util.CsvWriter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/budgets/actual-costs/review-route-overrides")
@Tag(name = "actual-cost-route-overrides")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ActualCostReviewRouteOverrideController {

    private final ActualCostReviewRouteOverrideService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_READ')")
    public ResponseEntity<PageResponseWithSummary<ActualCostReviewRouteOverrideResponseDto, ActualCostReviewRouteOverrideRegistrySummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ActualCostReviewRouteOverrideResponseDto> items = service.findActive();
        return ResponseEntity.ok(PageResponseWithSummary.of(items, page, size, summary(items)));
    }

    @GetMapping("/by-actual-cost/{actualCostId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_READ')")
    public ResponseEntity<Page<ActualCostReviewRouteOverrideResponseDto>> byActualCost(@PathVariable UUID actualCostId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByActualCost(actualCostId), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_APPLY')")
    public ResponseEntity<ActualCostReviewRouteOverrideResponseDto> apply(
            @Valid @RequestBody ActualCostReviewRouteOverrideCreateRequest r) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.apply(r));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_CLEAR')")
    public ResponseEntity<ActualCostReviewRouteOverrideDto> deactivate(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam String comment) {
        return ResponseEntity.ok(service.deactivate(id, userId, comment));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_READ')")
    public ResponseEntity<String> export() {
        List<ActualCostReviewRouteOverrideResponseDto> items = service.findActive();
        String csv = CsvWriter.build(
                List.of("id", "actualCostId", "approvalRoleCode", "escalationRoleCode", "thresholdHours", "isActive"),
                items,
                List.of(
                        ActualCostReviewRouteOverrideResponseDto::id,
                        item -> item.actualCost() != null ? item.actualCost().id() : null,
                        ActualCostReviewRouteOverrideResponseDto::approvalRoleCode,
                        ActualCostReviewRouteOverrideResponseDto::escalationRoleCode,
                        ActualCostReviewRouteOverrideResponseDto::thresholdHours,
                        ActualCostReviewRouteOverrideResponseDto::isActive
                )
        );
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"actual-cost-review-route-overrides.csv\"")
                .body(csv);
    }

    @PostMapping("/bulk-apply")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_APPLY')")
    public ResponseEntity<BulkRouteOverrideApplyResponse> bulkApply(@RequestBody BulkApplyRequest request) {
        List<UUID> ids = request != null ? request.ids() : List.of();
        List<BulkRouteOverrideApplyResponse.Success> successes = new ArrayList<>();
        List<BulkRouteOverrideApplyResponse.Failure> failures = new ArrayList<>();
        for (UUID id : ids) {
            try {
                ActualCostReviewRouteOverrideResponseDto response = service.apply(new ActualCostReviewRouteOverrideCreateRequest(
                        id,
                        request.departmentId(),
                        request.approvalRoleCode(),
                        request.escalationRoleCode(),
                        request.thresholdHours(),
                        request.comment()
                ));
                successes.add(new BulkRouteOverrideApplyResponse.Success(id, response.id()));
            } catch (RuntimeException ex) {
                failures.add(new BulkRouteOverrideApplyResponse.Failure(id, ex.getMessage()));
            }
        }
        return ResponseEntity.ok(new BulkRouteOverrideApplyResponse("APPLY", ids.size(), successes.size(), failures.size(), successes, failures));
    }

    @PostMapping("/bulk-clear")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_CLEAR')")
    public ResponseEntity<BulkRouteOverrideClearResponse> bulkClear(@RequestBody BulkClearRequest request) {
        List<UUID> ids = request != null ? request.ids() : List.of();
        List<BulkRouteOverrideClearResponse.Success> successes = new ArrayList<>();
        List<BulkRouteOverrideClearResponse.Failure> failures = new ArrayList<>();
        for (UUID id : ids) {
            try {
                List<UUID> cleared = service.deactivateActiveForActualCost(id, new UUID(0, 0), request.comment());
                successes.add(new BulkRouteOverrideClearResponse.Success(id, cleared));
            } catch (RuntimeException ex) {
                failures.add(new BulkRouteOverrideClearResponse.Failure(id, ex.getMessage()));
            }
        }
        return ResponseEntity.ok(new BulkRouteOverrideClearResponse("CLEAR", ids.size(), successes.size(), failures.size(), successes, failures));
    }

    private ActualCostReviewRouteOverrideRegistrySummary summary(List<ActualCostReviewRouteOverrideResponseDto> items) {
        int active = (int) items.stream().filter(item -> Boolean.TRUE.equals(item.isActive())).count();
        int inactive = items.size() - active;
        int overdue = (int) items.stream()
                .filter(item -> item.actualCost() != null && Boolean.TRUE.equals(item.actualCost().isOverdue()))
                .count();
        int dueSoon = (int) items.stream()
                .filter(item -> item.actualCost() != null
                        && !Boolean.TRUE.equals(item.actualCost().isOverdue())
                        && item.actualCost().hoursToOverdue() != null
                        && item.actualCost().hoursToOverdue() <= 4)
                .count();
        int uniqueActualCosts = (int) items.stream()
                .map(item -> item.actualCost() != null ? item.actualCost().id() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();
        int uniqueDepartments = (int) items.stream()
                .map(ActualCostReviewRouteOverrideResponseDto::departmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();
        return new ActualCostReviewRouteOverrideRegistrySummary(
                items.size(),
                active,
                inactive,
                overdue,
                dueSoon,
                uniqueActualCosts,
                uniqueDepartments
        );
    }

    public record BulkApplyRequest(
            List<UUID> ids,
            UUID departmentId,
            String approvalRoleCode,
            String escalationRoleCode,
            Integer thresholdHours,
            String comment
    ) {
    }

    public record BulkClearRequest(List<UUID> ids, String comment) {
    }
}
