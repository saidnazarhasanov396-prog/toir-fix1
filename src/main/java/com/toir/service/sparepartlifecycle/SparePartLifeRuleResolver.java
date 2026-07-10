package com.toir.service.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.exception.RestException;
import com.toir.repository.sparepartlifecycle.SparePartLifeRuleRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartLifeRuleResolver {

    private final SparePartLifeRuleRepository ruleRepository;
    private final SparePartSlotNormalizer slotNormalizer;

    @Transactional(readOnly = true)
    public Optional<SparePartLifeRule> resolve(UUID sparePartId,
                                               UUID equipmentId,
                                               UUID equipmentNodeId,
                                               String slotCode,
                                               Instant at) {
        if (sparePartId == null || equipmentId == null) {
            throw RestException.badRequest("RULE_SCOPE_REQUIRED: sparePartId and equipmentId are required");
        }
        Instant effectiveAt = at == null ? Instant.now() : at;
        String normalizedSlot = slotNormalizer.normalizeNullable(slotCode);
        List<ScoredRule> matching = ruleRepository
                .findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(sparePartId)
                .stream()
                .filter(rule -> isEffective(rule, effectiveAt))
                .map(rule -> new ScoredRule(rule, precedence(rule, equipmentId, equipmentNodeId, normalizedSlot)))
                .filter(candidate -> candidate.precedence() > 0)
                .sorted(Comparator.comparingInt(ScoredRule::precedence).reversed())
                .toList();
        if (matching.isEmpty()) {
            return Optional.empty();
        }
        int highestPrecedence = matching.getFirst().precedence();
        List<SparePartLifeRule> highest = matching.stream()
                .filter(candidate -> candidate.precedence() == highestPrecedence)
                .map(ScoredRule::rule)
                .toList();
        if (highest.size() > 1) {
            throw RestException.conflict(
                    "RULE_AMBIGUOUS: multiple effective spare-part life rules match the same highest-precedence scope");
        }
        return Optional.of(highest.getFirst());
    }

    private static boolean isEffective(SparePartLifeRule rule, Instant at) {
        return (rule.getEffectiveFrom() == null || !rule.getEffectiveFrom().isAfter(at))
                && (rule.getEffectiveTo() == null || rule.getEffectiveTo().isAfter(at));
    }

    private static int precedence(SparePartLifeRule rule,
                                  UUID equipmentId,
                                  UUID equipmentNodeId,
                                  String normalizedSlot) {
        SparePartLifeRuleScope scope = rule.getScopeType();
        if (scope == null) {
            return 0;
        }
        return switch (scope) {
            case NODE_SLOT -> Objects.equals(rule.getEquipmentId(), equipmentId)
                    && Objects.equals(rule.getEquipmentNodeId(), equipmentNodeId)
                    && Objects.equals(rule.getNormalizedSlotCode(), normalizedSlot) ? 4 : 0;
            case NODE -> Objects.equals(rule.getEquipmentId(), equipmentId)
                    && Objects.equals(rule.getEquipmentNodeId(), equipmentNodeId)
                    && rule.getNormalizedSlotCode() == null ? 3 : 0;
            case EQUIPMENT -> Objects.equals(rule.getEquipmentId(), equipmentId)
                    && rule.getEquipmentNodeId() == null
                    && rule.getNormalizedSlotCode() == null ? 2 : 0;
            case CATALOG -> rule.getEquipmentId() == null
                    && rule.getEquipmentNodeId() == null
                    && rule.getNormalizedSlotCode() == null ? 1 : 0;
        };
    }

    private record ScoredRule(SparePartLifeRule rule, int precedence) {
    }
}
