package com.toir.service;
import com.toir.entity.SlaRule;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.SlaRuleRepository;

import com.toir.exception.RestException;
import com.toir.dto.sla.SlaRuleDto;
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
public class SlaRuleService {

    private final SlaRuleRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<SlaRuleDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(SlaRuleDto::from).toList();
    }

    public SlaRuleDto create(SlaRuleDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("SLA rule code already exists: " + r.code());
        }
        SlaRule rule = new SlaRule();
        apply(rule, r);
        SlaRule saved = repository.save(rule);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return SlaRuleDto.from(saved);
    }

    public SlaRuleDto update(UUID id, SlaRuleDto r) {
        SlaRule rule = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(rule);
        apply(rule, r);
        audit(AuditAction.UPDATE, rule.getId(), oldJson, rule);
        return SlaRuleDto.from(rule);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        SlaRule saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

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

    private void audit(AuditAction action, UUID id, String oldJson, SlaRule current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log("sla_rule", id != null ? id.toString() : null, action,
                AuditModule.SLA_RULE, auditMessage(action), oldJson, newJson);
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "SLA правило создано";
            case UPDATE -> "SLA правило обновлено";
            case DELETE -> "SLA правило удалено";
            default -> "Действие выполнено над SLA правилом";
        };
    }
}
