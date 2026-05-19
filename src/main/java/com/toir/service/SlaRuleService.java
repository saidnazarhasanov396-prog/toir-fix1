package com.toir.service;

import com.toir.dto.sla.SlaRuleDto;
import com.toir.entity.SlaRule;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.SlaRuleRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlaRuleService {

    private final SlaRuleRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<SlaRuleDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(SlaRuleDto::from).toList();
    }

    @Transactional
    public SlaRuleDto create(SlaRuleDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("SLA rule code already exists: " + r.code());
        }
        SlaRule rule = new SlaRule();
        apply(rule, r);
        SlaRule saved = repository.save(rule);

        auditBuilderService.log(
                "sla_rule",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SLA_RULE,
               "SLA правило создано",
                null,
                saved);

        return SlaRuleDto.from(saved);
    }

    @Transactional
    public SlaRuleDto update(UUID id, SlaRuleDto r) {
        SlaRule rule = getOrThrow(id);
        apply(rule, r);
        SlaRule saved = repository.save(rule);
        auditBuilderService.log(
                "sla_rule",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SLA_RULE,
                "SLA правило обновлено",
                rule,
                saved);

        return SlaRuleDto.from(rule);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        SlaRule saved = repository.save(entity);

        auditBuilderService.log(
                "sla_rule",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.SLA_RULE,
                "SLA правило удалено",
                saved,
                null);
    }

    private SlaRule getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("SLA rule not found: " + id));
    }

    private void apply(SlaRule rule, SlaRuleDto r) {
        rule.setCode(r.code());
        rule.setName(r.name());
        rule.setEntityType(r.entityType());
        rule.setTriggerType(r.triggerType());
        rule.setThresholdHours(r.thresholdHours());
        rule.setDepartmentId(r.departmentId());
        if (r.active() != null) rule.setActive(r.active());
    }
}
