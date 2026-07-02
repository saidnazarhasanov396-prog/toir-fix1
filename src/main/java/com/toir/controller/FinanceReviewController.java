package com.toir.controller;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.ActualCostService;
import com.toir.service.finance.ProcurementBudgetAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance-review")
@RequiredArgsConstructor
public class FinanceReviewController {

    private final ProcurementBudgetAllocationService procurementBudgetAllocationService;
    private final ActualCostService actualCostService;

    @GetMapping("/procurement-requests")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_REVIEW_READ')")
    public List<ProcurementRequestDto> getProcurementReviewQueue(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) ProcurementRequestStatus status,
            @RequestParam(required = false, defaultValue = "false") Boolean unallocatedOnly) {

        return procurementBudgetAllocationService.reviewQueue(
                departmentId,
                status,
                Boolean.TRUE.equals(unallocatedOnly));
    }

    @PostMapping("/procurement-requests/{id}/allocate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_REVIEW_ALLOCATE')")
    public ProcurementRequestDto allocateProcurementBudget(
            @PathVariable UUID id,
            @RequestBody BudgetAllocationRequest request,
            @CurrentUser AuthenticatedUser currentUser) {

        return procurementBudgetAllocationService.allocateBudget(
                id,
                request.budgetLineId(),
                UUID.fromString(currentUser.id()),
                request.comment());
    }

    @PostMapping("/procurement-requests/{id}/unallocate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_REVIEW_UNALLOCATE')")
    public ProcurementRequestDto unallocateProcurementBudget(
            @PathVariable UUID id,
            @RequestBody BudgetUnallocationRequest request,
            @CurrentUser AuthenticatedUser currentUser) {

        return procurementBudgetAllocationService.unallocateBudget(
                id,
                UUID.fromString(currentUser.id()),
                request.comment());
    }

    @PostMapping("/actual-costs/{id}/allocate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_ALLOCATE')")
    public ActualCostDto allocateActualCostBudget(
            @PathVariable UUID id,
            @RequestBody BudgetAllocationRequest request,
            @CurrentUser AuthenticatedUser currentUser) {

        return actualCostService.allocateBudgetLine(
                id,
                request.budgetLineId(),
                UUID.fromString(currentUser.id()),
                request.comment());
    }

    public record BudgetAllocationRequest(UUID budgetLineId, String comment) {
    }

    public record BudgetUnallocationRequest(String comment) {
    }
}
