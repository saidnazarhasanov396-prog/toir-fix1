package com.toir.service;
import com.toir.entity.FinancialApprovalRule;
import com.toir.repository.FinancialApprovalRuleRepository;

import com.toir.exception.RestException;
import com.toir.dto.financialapprovalrule.FinancialApprovalRuleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialApprovalRuleService {

    private final FinancialApprovalRuleRepository repository;


    @Transactional(readOnly = true)
    public List<FinancialApprovalRuleDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(FinancialApprovalRuleDto::from).toList();
    }

    public FinancialApprovalRuleDto create(FinancialApprovalRuleDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Approval rule code already exists: " + r.code());
        }
        FinancialApprovalRule rule = new FinancialApprovalRule();
        apply(rule, r);
        return FinancialApprovalRuleDto.from(repository.save(rule));
    }

    public FinancialApprovalRuleDto update(UUID id, FinancialApprovalRuleDto r) {
        FinancialApprovalRule rule = getOrThrow(id);
        apply(rule, r);
        return FinancialApprovalRuleDto.from(rule);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

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
}
