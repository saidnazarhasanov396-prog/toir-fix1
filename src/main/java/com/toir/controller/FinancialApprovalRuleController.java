package com.toir.controller;
import com.toir.dto.financialapprovalrule.FinancialApprovalRuleDto;
import com.toir.security.RequiresAdmin;
import com.toir.service.FinancialApprovalRuleService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budgets/approval-rules")
@Tag(name = "financial-approval-rules")
@RequiresAdmin
@RequiredArgsConstructor
public class FinancialApprovalRuleController {

    private final FinancialApprovalRuleService service;


    @GetMapping
    public ResponseEntity<Page<FinancialApprovalRuleDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(departmentId, role, status, search), page, size));
    }

    @PostMapping
    public ResponseEntity<FinancialApprovalRuleDto> create(@Valid @RequestBody FinancialApprovalRuleDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinancialApprovalRuleDto> update(@PathVariable UUID id, @Valid @RequestBody FinancialApprovalRuleDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FinancialApprovalRuleDto> patch(@PathVariable UUID id, @Valid @RequestBody FinancialApprovalRuleDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
