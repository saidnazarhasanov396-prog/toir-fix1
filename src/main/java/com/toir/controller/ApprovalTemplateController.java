package com.toir.controller;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.approval.ApprovalRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approval-templates")
@Tag(name = "approval-templates")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ApprovalTemplateController {

    private final ApprovalRuleService ruleService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_READ') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<List<ApprovalRuleDto>> list() {
        return ResponseEntity.ok(ruleService.listRules());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_CREATE') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<ApprovalRuleDto> create(@Valid @RequestBody ApprovalRuleDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.saveRule(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_READ') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<ApprovalRuleDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ruleService.getRule(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_UPDATE') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<ApprovalRuleDto> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ApprovalRuleDto request) {
        return ResponseEntity.ok(ruleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('APPROVAL_DELETE') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
