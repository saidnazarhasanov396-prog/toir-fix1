package com.toir.service;
import com.toir.entity.FinancialApprovalRule;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.FinancialApprovalRuleRepository;

import com.toir.exception.RestException;
import com.toir.dto.financialapprovalrule.FinancialApprovalRuleDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FinancialApprovalRuleService {

    private final FinancialApprovalRuleRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<FinancialApprovalRuleDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(FinancialApprovalRuleDto::from).toList();
    }

    public FinancialApprovalRuleDto create(FinancialApprovalRuleDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Approval rule code already exists: " + r.code());
        }
        FinancialApprovalRule rule = new FinancialApprovalRule();
        apply(rule, r);
        FinancialApprovalRule saved = repository.save(rule);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return FinancialApprovalRuleDto.from(saved);
    }

    public FinancialApprovalRuleDto update(UUID id, FinancialApprovalRuleDto r) {
        FinancialApprovalRule rule = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(rule);
        apply(rule, r);
        audit(AuditAction.UPDATE, rule.getId(), oldJson, rule);
        return FinancialApprovalRuleDto.from(rule);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        FinancialApprovalRule saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private FinancialApprovalRule getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval rule not found: " + id));
    }

    private void apply(FinancialApprovalRule rule, FinancialApprovalRuleDto r) {
        rule.setCode(r.code());
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

    private void audit(AuditAction action, UUID id, String oldJson, FinancialApprovalRule current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log("financial_approval_rule", id != null ? id.toString() : null, action,
                AuditModule.FINANCIAL_APPROVAL_RULE, auditMessage(action), oldJson, newJson);
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Правило финансового согласования создано";
            case UPDATE -> "Правило финансового согласования обновлено";
            case DELETE -> "Правило финансового согласования удалено";
            default -> "Действие выполнено над правилом финансового согласования";
        };
    }
}
