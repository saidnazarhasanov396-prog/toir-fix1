package com.toir.dto.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartLifeRuleListDto(
        UUID id,
        UUID sparePartId,
        @Schema(nullable = true)
        String sparePartName,
        UUID equipmentId,
        @Schema(nullable = true)
        String equipmentName,
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
        List<SparePartLifeLimitListDto> limits,
        Instant createdAt,
        Instant updatedAt
) {
    public static SparePartLifeRuleListDto from(
            SparePartLifeRule rule,
            String sparePartName,
            String equipmentName,
            List<SparePartLifeLimit> limits
    ) {
        return new SparePartLifeRuleListDto(
                rule.getId(),
                rule.getSparePartId(),
                sparePartName,
                rule.getEquipmentId(),
                equipmentName,
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
                limits.stream().map(SparePartLifeLimitListDto::from).toList(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
