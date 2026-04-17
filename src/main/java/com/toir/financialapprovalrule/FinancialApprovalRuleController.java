package com.toir.financialapprovalrule;

import com.toir.common.security.RequiresAdmin;
import com.toir.financialapprovalrule.dto.FinancialApprovalRuleDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets/approval-rules")
@Tag(name = "financial-approval-rules")
@RequiresAdmin
public class FinancialApprovalRuleController {

    private final FinancialApprovalRuleService service;

    public FinancialApprovalRuleController(FinancialApprovalRuleService service) { this.service = service; }

    @GetMapping
    public List<FinancialApprovalRuleDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<FinancialApprovalRuleDto> create(@Valid @RequestBody FinancialApprovalRuleDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public FinancialApprovalRuleDto update(@PathVariable UUID id, @Valid @RequestBody FinancialApprovalRuleDto r) {
        return service.update(id, r);
    }

    @PatchMapping("/{id}")
    public FinancialApprovalRuleDto patch(@PathVariable UUID id, @Valid @RequestBody FinancialApprovalRuleDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
