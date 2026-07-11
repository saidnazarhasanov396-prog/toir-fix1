package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeLimitRequest;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleDto;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeLimitRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeRuleRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeRuleSpecifications;
import com.toir.util.AuditBuilderService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartLifeRuleService {

    private final SparePartLifeRuleRepository ruleRepository;
    private final SparePartLifeLimitRepository limitRepository;
    private final SparePartRepository sparePartRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentNodeRepository equipmentNodeRepository;
    private final SparePartSlotNormalizer slotNormalizer;
    private final SparePartLifeRuleValidator validator;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public Page<SparePartLifeRuleDto> list(SparePartLifeRuleFilter filter, Pageable pageable) {
        Page<SparePartLifeRule> page = ruleRepository.findAll(
                SparePartLifeRuleSpecifications.byFilter(filter), pageable);
        List<SparePartLifeRule> rules = page.getContent();
        if (rules.isEmpty()) {
            return page.map(rule -> SparePartLifeRuleDto.from(rule, List.of()));
        }
        // N+1 dan qochish uchun barcha limitlarni bitta so'rovda olib, rule bo'yicha guruhlaymiz.
        List<UUID> ruleIds = rules.stream().map(SparePartLifeRule::getId).toList();
        Map<UUID, List<SparePartLifeLimit>> limitsByRule = limitRepository
                .findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(ruleIds)
                .stream()
                .collect(Collectors.groupingBy(SparePartLifeLimit::getRuleId));
        return page.map(rule -> SparePartLifeRuleDto.from(
                rule,
                limitsByRule.getOrDefault(rule.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public SparePartLifeRuleDto get(UUID id) {
        return toDto(getEntity(id));
    }

    @Transactional
    public SparePartLifeRuleDto create(SparePartLifeRuleRequest request) {
        validator.validate(request);
        Scope scope = validateAndDeriveScope(request);
        assertNoOverlap(request, scope, null);

        SparePartLifeRule rule = new SparePartLifeRule();
        rule.setSparePartId(request.sparePartId());
        rule.setEquipmentId(request.equipmentId());
        rule.setEquipmentNodeId(request.equipmentNodeId());
        rule.setNormalizedSlotCode(scope.normalizedSlotCode());
        rule.setScopeType(scope.scopeType());
        rule.setCombinationMode(request.combinationMode());
        rule.setDueAction(request.dueAction());
        rule.setActive(request.active() == null || request.active());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setRevision(nextRevision(request, scope));
        rule.setName(normalizeText(request.name()));
        rule.setDescription(normalizeText(request.description()));
        SparePartLifeRule savedRule = ruleRepository.save(rule);

        List<SparePartLifeLimit> savedLimits = saveLimits(savedRule.getId(), request.limits());
        SparePartLifeRuleDto result = SparePartLifeRuleDto.from(savedRule, savedLimits);
        auditBuilderService.log(
                "spare_part_life_rule",
                savedRule.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "Spare-part life rule created",
                null,
                result
        );
        return result;
    }

    @Transactional
    public SparePartLifeRuleDto revise(UUID id, SparePartLifeRuleRequest request) {
        SparePartLifeRule previous = getEntity(id);
        if (!Objects.equals(previous.getSparePartId(), request.sparePartId())) {
            throw RestException.conflict("RULE_REVISION_PART_IMMUTABLE: revision cannot change the spare part");
        }
        // Revizya aynan bir xil scope tuple ustida ishlaydi: equipment/node/slot ni o'zgartira olmaydi.
        String requestedSlot = slotNormalizer.normalizeNullable(request.slotCode());
        if (!Objects.equals(previous.getEquipmentId(), request.equipmentId())
                || !Objects.equals(previous.getEquipmentNodeId(), request.equipmentNodeId())
                || !Objects.equals(previous.getNormalizedSlotCode(), requestedSlot)) {
            throw RestException.conflict(
                    "RULE_REVISION_PART_IMMUTABLE: revision cannot change the equipment, node or slot scope");
        }
        // Eski revizyani yopamiz, so'ng yangi revizyani yaratamiz. create() muvaffaqiyatsiz
        // bo'lsa, @Transactional tufayli quyidagi deaktivatsiya ham rollback bo'ladi.
        previous.setActive(false);
        if (previous.getEffectiveTo() == null) {
            previous.setEffectiveTo(Instant.now());
        }
        ruleRepository.save(previous);
        return create(request);
    }

    @Transactional
    public void deactivate(UUID id) {
        SparePartLifeRule rule = getEntity(id);
        rule.setActive(false);
        ruleRepository.save(rule);
        auditBuilderService.log(
                "spare_part_life_rule",
                rule.getId().toString(),
                AuditAction.DELETE,
                AuditModule.SPARE_PART,
                "Spare-part life rule deactivated",
                null,
                rule
        );
    }

    private SparePartLifeRule getEntity(UUID id) {
        return ruleRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Spare-part life rule not found: " + id));
    }

    private SparePartLifeRuleDto toDto(SparePartLifeRule rule) {
        return SparePartLifeRuleDto.from(
                rule,
                limitRepository.findAllByRuleIdAndIsDeletedFalseOrderBySequenceAsc(rule.getId())
        );
    }

    private Scope validateAndDeriveScope(SparePartLifeRuleRequest request) {
        sparePartRepository.findByIdAndIsDeletedFalse(request.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + request.sparePartId()));
        String normalizedSlot = slotNormalizer.normalizeNullable(request.slotCode());
        if (request.equipmentId() == null) {
            if (request.equipmentNodeId() != null || normalizedSlot != null) {
                throw RestException.badRequest("RULE_SCOPE_INVALID: node and slot scopes require equipment");
            }
            return new Scope(SparePartLifeRuleScope.CATALOG, null);
        }
        equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + request.equipmentId()));
        if (request.equipmentNodeId() == null) {
            if (normalizedSlot != null) {
                throw RestException.badRequest("RULE_SCOPE_INVALID: slot scope requires an equipment node");
            }
            return new Scope(SparePartLifeRuleScope.EQUIPMENT, null);
        }
        EquipmentNode node = equipmentNodeRepository.findByIdAndIsDeletedFalse(request.equipmentNodeId())
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + request.equipmentNodeId()));
        if (!Objects.equals(node.getEquipmentId(), request.equipmentId())) {
            throw RestException.conflict("RULE_NODE_MISMATCH: equipment node does not belong to the selected equipment");
        }
        return new Scope(
                normalizedSlot == null ? SparePartLifeRuleScope.NODE : SparePartLifeRuleScope.NODE_SLOT,
                normalizedSlot
        );
    }

    private void assertNoOverlap(SparePartLifeRuleRequest request, Scope scope, UUID excludedRuleId) {
        if (Boolean.FALSE.equals(request.active())) {
            return;
        }
        boolean overlap = ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(request.sparePartId())
                .stream()
                .filter(existing -> !Objects.equals(existing.getId(), excludedRuleId))
                .filter(existing -> existing.getScopeType() == scope.scopeType())
                .filter(existing -> Objects.equals(existing.getEquipmentId(), request.equipmentId()))
                .filter(existing -> Objects.equals(existing.getEquipmentNodeId(), request.equipmentNodeId()))
                .filter(existing -> Objects.equals(existing.getNormalizedSlotCode(), scope.normalizedSlotCode()))
                .anyMatch(existing -> intervalsOverlap(
                        existing.getEffectiveFrom(),
                        existing.getEffectiveTo(),
                        request.effectiveFrom(),
                        request.effectiveTo()
                ));
        if (overlap) {
            throw RestException.conflict("RULE_SCOPE_OVERLAP: an active rule already covers this exact scope and interval");
        }
    }

    private int nextRevision(SparePartLifeRuleRequest request, Scope scope) {
        // Keyingi revizya raqami aynan bir xil scope tuple (part + scope + equipment + node + slot)
        // bo'yicha hisoblanadi, faqat scope turi bo'yicha emas.
        return ruleRepository.findAllBySparePartIdAndIsDeletedFalse(request.sparePartId()).stream()
                .filter(rule -> rule.getScopeType() == scope.scopeType())
                .filter(rule -> Objects.equals(rule.getEquipmentId(), request.equipmentId()))
                .filter(rule -> Objects.equals(rule.getEquipmentNodeId(), request.equipmentNodeId()))
                .filter(rule -> Objects.equals(rule.getNormalizedSlotCode(), scope.normalizedSlotCode()))
                .map(SparePartLifeRule::getRevision)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private List<SparePartLifeLimit> saveLimits(UUID ruleId, List<SparePartLifeLimitRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<SparePartLifeLimit> limits = requests.stream()
                .sorted(Comparator.comparingInt(SparePartLifeLimitRequest::sequence))
                .map(request -> toEntity(ruleId, request))
                .toList();
        return limitRepository.saveAll(limits);
    }

    private static SparePartLifeLimit toEntity(UUID ruleId, SparePartLifeLimitRequest request) {
        SparePartLifeLimit limit = new SparePartLifeLimit();
        limit.setRuleId(ruleId);
        limit.setLimitKind(request.limitKind());
        limit.setCalendarUnit(request.calendarUnit());
        limit.setMeterType(request.meterType());
        limit.setExplicitEquipmentMeterId(request.explicitEquipmentMeterId());
        limit.setLimitValue(request.limitValue());
        limit.setWarningBeforeValue(request.warningBeforeValue());
        limit.setSequence(request.sequence());
        return limit;
    }

    private static boolean intervalsOverlap(Instant firstStart,
                                            Instant firstEnd,
                                            Instant secondStart,
                                            Instant secondEnd) {
        return (firstEnd == null || secondStart == null || firstEnd.isAfter(secondStart))
                && (secondEnd == null || firstStart == null || secondEnd.isAfter(firstStart));
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Scope(SparePartLifeRuleScope scopeType, String normalizedSlotCode) {
    }
}
