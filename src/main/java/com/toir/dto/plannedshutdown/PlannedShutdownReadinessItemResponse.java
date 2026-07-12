package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownReadinessSeverity;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownReadinessItemResponse(UUID id, String readinessKey, String sourceType, UUID sourceId,
        String title, PlannedShutdownReadinessSeverity severity, PlannedShutdownItemStatus status,
        UUID responsibleEmployeeId, Instant dueAt, String evidence, String comment,
        UUID completedById, Instant completedAt, Integer orderNumber) {
    public static PlannedShutdownReadinessItemResponse from(PlannedShutdownReadinessItem item) {
        return new PlannedShutdownReadinessItemResponse(item.getId(), item.getReadinessKey(), item.getSourceType(),
                item.getSourceId(), item.getTitle(), item.getSeverity(), item.getStatus(),
                item.getResponsibleEmployeeId(), item.getDueAt(), item.getEvidence(), item.getComment(),
                item.getCompletedById(), item.getCompletedAt(), item.getOrderNumber());
    }
}
