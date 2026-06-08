package com.toir.dto.safetychecklist;

import com.toir.entity.maintenance.WorkOrderSafetyChecklistItem;
import com.toir.enums.SafetyChecklistCategory;
import com.toir.enums.SafetyChecklistItemStatus;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderSafetyChecklistItemDto(
        UUID id,
        int sequence,
        String label,
        SafetyChecklistCategory category,
        boolean critical,
        SafetyChecklistItemStatus status,
        String comment,
        UUID checkedById,
        Instant checkedAt
) {
    public static WorkOrderSafetyChecklistItemDto from(WorkOrderSafetyChecklistItem item) {
        return new WorkOrderSafetyChecklistItemDto(
                item.getId(),
                item.getSequence(),
                item.getLabel(),
                item.getCategory(),
                item.isCritical(),
                item.getStatus(),
                item.getComment(),
                item.getCheckedById(),
                item.getCheckedAt());
    }
}
