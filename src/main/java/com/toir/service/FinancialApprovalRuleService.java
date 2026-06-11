package com.toir.service;

import com.toir.dto.financialapprovalrule.FinancialApprovalRuleDto;
import com.toir.entity.projects.FinancialApprovalRule;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialApprovalRuleService {

    private final FinancialApprovalRuleRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<FinancialApprovalRuleDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(FinancialApprovalRuleDto::from).toList();
    }

    @Transactional
    public FinancialApprovalRuleDto create(FinancialApprovalRuleDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        FinancialApprovalRule rule = new FinancialApprovalRule();
        rule.setCode(nextCode());
        apply(rule, r);
        FinancialApprovalRule saved = repository.save(rule);

        auditBuilderService.log(
                "financial_approval_rule",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.FINANCIAL_APPROVAL_RULE,
                "Правило финансового согласования создано",
                null,
                saved);


        return FinancialApprovalRuleDto.from(saved);
    }

    @Transactional
    public FinancialApprovalRuleDto update(UUID id, FinancialApprovalRuleDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        FinancialApprovalRule rule = getOrThrow(id);
        apply(rule, r);

        FinancialApprovalRule saved = repository.save(rule);

        auditBuilderService.log(
                "financial_approval_rule",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.FINANCIAL_APPROVAL_RULE,
                "Правило финансового согласования обновлено",
                rule,
                saved);


        return FinancialApprovalRuleDto.from(rule);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        FinancialApprovalRule saved = repository.save(entity);

        auditBuilderService.log(
                "financial_approval_rule",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.FINANCIAL_APPROVAL_RULE,
                "Правило финансового согласования удалено",
                saved,
                null);
    }

    private FinancialApprovalRule getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval rule not found: " + id));
    }

    private void apply(FinancialApprovalRule rule, FinancialApprovalRuleDto r) {
        rule.setName(r.name());
        rule.setDepartmentId(r.departmentId());
        rule.setMinAmount(r.minAmount());
        rule.setMaxAmount(r.maxAmount());
        rule.setRequiredRoleCode(r.requiredRoleCode());
        rule.setEscalateToRoleCode(r.escalateToRoleCode());
        rule.setThresholdHours(r.thresholdHours());
        if (r.priority() != null) rule.setPriority(r.priority());
        rule.setNotes(r.notes());
        if (r.isActive() != null) rule.setActive(r.isActive());
    }

    private String nextCode() {
        String prefix = "FAR-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "FAR",
                () -> repository.maxSequenceByCodePrefix(prefix),
                repository::existsByCodeAndIsDeletedFalse
        );
    }
}
