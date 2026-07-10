package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import java.util.List;
import java.util.UUID;

public record AppliedLifeRuleSnapshot(
        UUID ruleId,
        int revision,
        SparePartLifeCombinationMode combinationMode,
        SparePartDueAction dueAction,
        List<AppliedLifeLimitSnapshot> limits
) {
}
