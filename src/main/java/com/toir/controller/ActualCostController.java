package com.toir.controller;
import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.enums.ApprovalActionType;
import com.toir.exception.RestException;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ApprovalService;
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
    private final ApprovalService approvalService;

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

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_APPROVE')")
    public ResponseEntity<ApprovalRequestDto> approve(@PathVariable UUID id,
                                                      @RequestParam(required = false) UUID reviewerId,
                                                      @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(approvalService.createOrReuseApprovalForDocument(
                "ACTUAL_COST",
                id,
                ApprovalActionType.APPROVE,
                null,
                reviewerId,
                "ACTUAL_COST_APPROVER",
                "Actual cost approval: " + id,
                comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_REJECT')")
    public ResponseEntity<ActualCostDto> reject(@PathVariable UUID id, @RequestParam UUID reviewerId, @RequestParam String comment) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }
}
