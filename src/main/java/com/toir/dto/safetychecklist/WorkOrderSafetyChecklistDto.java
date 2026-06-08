package com.toir.dto.safetychecklist;

import com.toir.entity.maintenance.WorkOrderSafetyChecklist;
import com.toir.entity.maintenance.WorkOrderSafetyChecklistItem;
import com.toir.enums.SafetyChecklistStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record WorkOrderSafetyChecklistDto(
        UUID id,
        UUID workOrderId,
        UUID templateId,
        SafetyChecklistStatus status,
        UUID checkedById,
        Instant checkedAt,
        String remarks,
        List<WorkOrderSafetyChecklistItemDto> items
) {
    public static WorkOrderSafetyChecklistDto from(WorkOrderSafetyChecklist checklist, List<WorkOrderSafetyChecklistItem> items) {
        return new WorkOrderSafetyChecklistDto(
                checklist.getId(),
                checklist.getWorkOrderId(),
                checklist.getTemplateId(),
                checklist.getStatus(),
                checklist.getCheckedById(),
                checklist.getCheckedAt(),
                checklist.getRemarks(),
                items.stream()
                        .sorted(Comparator.comparingInt(WorkOrderSafetyChecklistItem::getSequence))
                        .map(WorkOrderSafetyChecklistItemDto::from)
                        .toList());
    }
}
