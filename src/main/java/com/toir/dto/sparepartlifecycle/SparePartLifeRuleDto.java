package com.toir.dto.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartLifeRuleDto(
        UUID id,
        UUID sparePartId,
        UUID equipmentId,
        UUID equipmentNodeId,
        String normalizedSlotCode,
        SparePartLifeRuleScope scopeType,
        SparePartLifeCombinationMode combinationMode,
        SparePartDueAction dueAction,
        boolean active,
        Instant effectiveFrom,
        Instant effectiveTo,
        int revision,
        String name,
        String description,
        List<SparePartLifeLimitDto> limits,
        Instant createdAt,
        Instant updatedAt
) {
    public static SparePartLifeRuleDto from(SparePartLifeRule rule, List<SparePartLifeLimit> limits) {
        return new SparePartLifeRuleDto(
                rule.getId(),
                rule.getSparePartId(),
                rule.getEquipmentId(),
                rule.getEquipmentNodeId(),
                rule.getNormalizedSlotCode(),
                rule.getScopeType(),
                rule.getCombinationMode(),
                rule.getDueAction(),
                rule.isActive(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.getRevision(),
                rule.getName(),
                rule.getDescription(),
                limits.stream().map(SparePartLifeLimitDto::from).toList(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
