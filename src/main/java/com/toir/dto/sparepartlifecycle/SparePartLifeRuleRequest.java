package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartLifeRuleRequest(
        @NotNull UUID sparePartId,
        UUID equipmentId,
        UUID equipmentNodeId,
        String slotCode,
        @NotNull SparePartLifeCombinationMode combinationMode,
        @NotNull SparePartDueAction dueAction,
        Boolean active,
        Instant effectiveFrom,
        Instant effectiveTo,
        String name,
        String description,
        List<@Valid SparePartLifeLimitRequest> limits
) {
}
