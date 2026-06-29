package com.toir.controller;
import com.toir.dto.actualcost.ActualCostAllocationRequest;
import com.toir.dto.actualcost.ActualCostCorrectionRequest;
import com.toir.dto.actualcost.ActualCostDto;
import com.toir.exception.RestException;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.ActualCostService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/actual-costs")
@Tag(name = "actual-costs")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ActualCostController {

    private final ActualCostService service;

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<Page<ActualCostDto>> pending(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findPending(), page, size)); }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<Page<ActualCostDto>> list(@RequestParam(required = false) UUID workOrderId,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByFilters(workOrderId, search), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_CREATE')")
    public ResponseEntity<ActualCostDto> create(@Valid @RequestBody ActualCostDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_REJECT')")
    public ResponseEntity<ActualCostDto> reject(@PathVariable UUID id, @RequestParam UUID reviewerId, @RequestParam String comment) {
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Rejection comment is required");
        }
        return ResponseEntity.ok(service.review(id, false, reviewerId, comment));
    }

    @PostMapping("/{id}/allocate-budget-line")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_ALLOCATE')")
    public ResponseEntity<ActualCostDto> allocateBudgetLine(@PathVariable UUID id,
                                                            @CurrentUser AuthenticatedUser user,
                                                            @Valid @RequestBody ActualCostAllocationRequest request) {
        return ResponseEntity.ok(service.allocateBudgetLine(id, request.budgetLineId(), currentUserId(user), request.comment()));
    }

    @PostMapping("/{id}/request-correction")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_REQUEST_CORRECTION')")
    public ResponseEntity<ActualCostDto> requestCorrection(@PathVariable UUID id,
                                                           @CurrentUser AuthenticatedUser user,
                                                           @Valid @RequestBody ActualCostCorrectionRequest request) {
        return ResponseEntity.ok(service.requestCorrection(id, currentUserId(user), request.comment()));
    }

    private UUID currentUserId(AuthenticatedUser user) {
        return user != null ? UUID.fromString(user.id()) : null;
    }
}
