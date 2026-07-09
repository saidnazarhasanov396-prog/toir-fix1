package com.toir.dto.maintenanceregulation;

import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import java.util.List;
import java.util.UUID;

public record MaintenanceRegulationImpactDto(
        UUID regulationId,
        long affectedEquipment,
        long matchedCount,
        long unmatchedCount,
        long blockedCount,
        long missingMetersCount,
        AutomationAction automationAction,
        ApprovalResultAction approvalResultAction,
        DuplicatePolicy duplicatePolicy,
        List<MaintenanceRegulationPreviewDto.Item> items
) {
}
