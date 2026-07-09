package com.toir.dto.maintenanceregulation;

import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceDueStatus;
import java.util.List;
import java.util.UUID;

public record MaintenanceRegulationPreviewDto(
        long affectedEquipment,
        long matchedCount,
        long unmatchedCount,
        long blockedCount,
        long missingMetersCount,
        AutomationAction automationAction,
        ApprovalResultAction approvalResultAction,
        DuplicatePolicy duplicatePolicy,
        List<Item> items
) {
    public record Item(
            UUID equipmentId,
            String equipmentCode,
            String equipmentName,
            boolean matched,
            boolean blocked,
            boolean missingMeter,
            String reason,
            MaintenanceDueStatus dueStatus,
            String reasonCode,
            String attributeKey
    ) {}
}
