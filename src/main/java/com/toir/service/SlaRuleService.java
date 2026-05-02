package com.toir.service;
import com.toir.entity.SlaRule;
import com.toir.repository.SlaRuleRepository;

import com.toir.exception.RestException;
import com.toir.dto.sla.SlaRuleDto;
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
        return SlaRuleDto.from(repository.save(rule));
    }

    public SlaRuleDto update(UUID id, SlaRuleDto r) {
        SlaRule rule = getOrThrow(id);
        apply(rule, r);
        return SlaRuleDto.from(rule);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

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
