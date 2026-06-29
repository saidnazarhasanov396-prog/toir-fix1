package com.toir.controller.maintenance;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.security.RequiresSensitiveAccess;
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

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')")
    public ResponseEntity<Page<MaintenanceBudgetDto>> list(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(
                service.findFiltered(year, month, departmentId, sortBy, sortDir),
                page,
                size
        ));
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

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_UPDATE')")
    public ResponseEntity<BudgetLineDto> addLine(@PathVariable UUID id, @Valid @RequestBody BudgetLineDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLine(id, r));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_UPDATE')")
    public ResponseEntity<MaintenanceBudgetDto> submit(@PathVariable UUID id,
                                                       @RequestBody(required = false) BudgetCommandRequest request) {
        return ResponseEntity.ok(service.submit(id, null, comment(request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_APPROVE')")
    public ResponseEntity<MaintenanceBudgetDto> approve(@PathVariable UUID id,
                                                        @RequestBody(required = false) BudgetCommandRequest request) {
        return ResponseEntity.ok(service.approve(id, null, comment(request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_APPROVE')")
    public ResponseEntity<MaintenanceBudgetDto> reject(@PathVariable UUID id,
                                                       @RequestBody(required = false) BudgetCommandRequest request) {
        return ResponseEntity.ok(service.reject(id, null, comment(request)));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_UPDATE')")
    public ResponseEntity<MaintenanceBudgetDto> lock(@PathVariable UUID id,
                                                     @RequestBody(required = false) BudgetCommandRequest request) {
        return ResponseEntity.ok(service.lock(id, null, comment(request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_CLOSE')")
    public ResponseEntity<MaintenanceBudgetDto> close(@PathVariable UUID id,
                                                      @RequestBody(required = false) BudgetCommandRequest request) {
        return ResponseEntity.ok(service.close(id, null, comment(request)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_REOPEN')")
    public ResponseEntity<MaintenanceBudgetDto> reopen(@PathVariable UUID id,
                                                       @RequestBody(required = false) BudgetCommandRequest request) {
        return ResponseEntity.ok(service.reopen(id, null, comment(request)));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_TRANSFER')")
    public ResponseEntity<MaintenanceBudgetDto> transfer(@PathVariable UUID id,
                                                         @RequestBody BudgetTransferRequest request) {
        return ResponseEntity.ok(service.transfer(
                id,
                request.fromBudgetLineId(),
                request.toBudgetLineId(),
                request.amount(),
                null,
                request.comment()
        ));
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_REVISE')")
    public ResponseEntity<MaintenanceBudgetDto> revise(@PathVariable UUID id,
                                                       @RequestBody BudgetRevisionRequest request) {
        return ResponseEntity.ok(service.reviseLine(
                id,
                request.budgetLineId(),
                request.plannedAmount(),
                null,
                request.comment()
        ));
    }

    private String comment(BudgetCommandRequest request) {
        return request != null ? request.comment() : null;
    }

    public record BudgetCommandRequest(String comment) {
    }

    public record BudgetTransferRequest(UUID fromBudgetLineId, UUID toBudgetLineId, double amount, String comment) {
    }

    public record BudgetRevisionRequest(UUID budgetLineId, double plannedAmount, String comment) {
    }
}
