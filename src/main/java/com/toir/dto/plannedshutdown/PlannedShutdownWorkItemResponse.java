package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PriorityLevel;

import java.util.UUID;

public record PlannedShutdownWorkItemResponse(
        UUID id, PlannedShutdownWorkItemSourceType sourceType, UUID sourceId, UUID equipmentId,
        String equipmentName, String title, PriorityLevel priority, boolean requiresShutdown, boolean requiresIsolation,
        Integer plannedDurationMinutes, String criticality, Integer orderNumber, PlannedShutdownItemStatus status) {
    public static PlannedShutdownWorkItemResponse from(PlannedShutdownWorkItem item) {
        return from(item, null);
    }

    public static PlannedShutdownWorkItemResponse from(PlannedShutdownWorkItem item, String equipmentName) {
        return new PlannedShutdownWorkItemResponse(item.getId(), item.getSourceType(), item.getSourceId(),
                item.getEquipmentId(), equipmentName, item.getTitle(), item.getPriority(), item.isRequiresShutdown(),
                item.isRequiresIsolation(), item.getPlannedDurationMinutes(), item.getCriticality(),
                item.getOrderNumber(), item.getStatus());
    }
}
