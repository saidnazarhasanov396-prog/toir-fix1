package com.toir.ai.repair;

import java.util.UUID;

public record AiRepairApplyResult(
        AiRepairAction action,
        UUID repairRequestId,
        String repairRequestNumber,
        AiRepairSkipReason skippedReason
) {
    public static AiRepairApplyResult skipped(AiRepairSkipReason reason) {
        return new AiRepairApplyResult(AiRepairAction.SKIPPED, null, null, reason);
    }

    public static AiRepairApplyResult applied(
            AiRepairAction action,
            UUID repairRequestId,
            String repairRequestNumber
    ) {
        return new AiRepairApplyResult(action, repairRequestId, repairRequestNumber, null);
    }
}
