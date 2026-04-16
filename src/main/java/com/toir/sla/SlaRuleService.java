package com.toir.sla;

import com.toir.common.exception.RestException;
import com.toir.sla.dto.SlaRuleDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SlaRuleService {

    private final SlaRuleRepository repository;

    public SlaRuleService(SlaRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SlaRuleDto> findAll() {
        return repository.findAll().stream().map(SlaRuleDto::from).toList();
    }

    public SlaRuleDto create(SlaRuleDto r) {
        if (repository.existsByCode(r.code())) {
            throw RestException.conflict("SLA rule code already exists: " + r.code());
        }
        SlaRule rule = new SlaRule();
        apply(rule, r);
        return SlaRuleDto.from(repository.save(rule));
    }

    public SlaRuleDto update(UUID id, SlaRuleDto r) {
        SlaRule rule = getOrThrow(id);
        apply(rule, r);
        return SlaRuleDto.from(rule);
    }

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private SlaRule getOrThrow(UUID id) {
        return repository.findById(id)
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
