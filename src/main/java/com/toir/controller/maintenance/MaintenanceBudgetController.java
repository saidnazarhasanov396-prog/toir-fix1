package com.toir.controller.maintenance;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ApprovalService;
import com.toir.service.maintanance.MaintenanceBudgetService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "budgets")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class MaintenanceBudgetController {

    private final MaintenanceBudgetService service;
    private final ApprovalService approvalService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')")
    public ResponseEntity<Page<MaintenanceBudgetDto>> list(@RequestParam int year,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByYear(year), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')")
    public ResponseEntity<MaintenanceBudgetDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_CREATE')")
    public ResponseEntity<MaintenanceBudgetDto> create(@Valid @RequestBody MaintenanceBudgetDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_APPROVE')")
    public ResponseEntity<MaintenanceBudgetDto> approve(@PathVariable UUID id,
            @RequestParam(required = false) UUID approverId) {
        MaintenanceBudgetDto current = service.validateCanApprove(id);
        approvalService.createOrReuseApprovalForDocument(
                "MAINTENANCE_BUDGET",
                id,
                approverId,
                approverId,
                "BUDGET_APPROVER",
                "Maintenance budget approval: " + current.year() + "/" + current.month(),
                "Approval workflow request for maintenance budget " + current.id());
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_UPDATE')")
    public ResponseEntity<BudgetLineDto> addLine(@PathVariable UUID id, @Valid @RequestBody BudgetLineDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLine(id, r));
    }
}
