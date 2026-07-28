package com.toir.service;

import com.toir.enums.NotificationEventType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NotificationNavigationBuilder {

    public NotificationNavigation forEntity(
            NotificationEventType eventType,
            String entityType,
            UUID entityId
    ) {
        return forEntity(eventType, entityType, entityId == null ? null : entityId.toString());
    }

    public NotificationNavigation forEntity(
            NotificationEventType eventType,
            String entityType,
            String entityId
    ) {
        String canonicalType = NotificationEntityTypes.normalize(entityType);
        String normalizedId = StringUtils.hasText(entityId) ? entityId.trim() : null;
        return new NotificationNavigation(
                eventType == null ? null : eventType.name(),
                canonicalType,
                normalizedId,
                route(canonicalType, normalizedId),
                Map.of()
        );
    }

    public NotificationNavigation forPprTask(
            NotificationEventType eventType,
            UUID taskId,
            UUID planId,
            List<UUID> workOrderIds
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (planId != null) {
            metadata.put("planId", planId.toString());
        }
        if (taskId != null) {
            metadata.put("taskId", taskId.toString());
        }
        List<UUID> distinctWorkOrderIds = workOrderIds == null
                ? List.of()
                : workOrderIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctWorkOrderIds.size() == 1) {
            UUID workOrderId = distinctWorkOrderIds.getFirst();
            metadata.put("secondaryActions", List.of(secondaryAction(
                    "notifications.actions.openWorkOrder",
                    NotificationEntityTypes.WORK_ORDER,
                    workOrderId.toString(),
                    route(NotificationEntityTypes.WORK_ORDER, workOrderId.toString())
            )));
        }
        String actionUrl = taskId != null && planId != null
                ? "/ppr-calendar/" + planId + "?taskId=" + taskId
                : null;
        return new NotificationNavigation(
                eventType == null ? null : eventType.name(),
                NotificationEntityTypes.PPR_TASK,
                taskId == null ? null : taskId.toString(),
                actionUrl,
                Map.copyOf(metadata)
        );
    }

    public NotificationNavigation forApprovalResult(
            NotificationEventType eventType,
            String ownerEntityType,
            UUID ownerEntityId,
            UUID approvalRequestId
    ) {
        NotificationNavigation owner = forEntity(eventType, ownerEntityType, ownerEntityId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (approvalRequestId != null) {
            metadata.put("approvalRequestId", approvalRequestId.toString());
            metadata.put("secondaryActions", List.of(secondaryAction(
                    "notifications.actions.openApprovalHistory",
                    NotificationEntityTypes.APPROVAL_REQUEST,
                    approvalRequestId.toString(),
                    route(NotificationEntityTypes.APPROVAL_REQUEST, approvalRequestId.toString())
            )));
        }
        return new NotificationNavigation(
                owner.eventType(),
                owner.entityType(),
                owner.entityId(),
                owner.actionUrl(),
                Map.copyOf(metadata)
        );
    }

    private Map<String, String> secondaryAction(
            String labelKey,
            String entityType,
            String entityId,
            String actionUrl
    ) {
        Map<String, String> action = new LinkedHashMap<>();
        action.put("labelKey", labelKey);
        action.put("entityType", entityType);
        action.put("entityId", entityId);
        action.put("actionUrl", actionUrl);
        return Map.copyOf(action);
    }

    private String route(String entityType, String entityId) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return null;
        }
        return switch (entityType) {
            case NotificationEntityTypes.WORK_ORDER -> "/work-orders/" + entityId;
            case NotificationEntityTypes.REPAIR_REQUEST -> "/repair-requests/" + entityId;
            case NotificationEntityTypes.PPR_PLAN -> "/ppr-calendar/" + entityId;
            case NotificationEntityTypes.APPROVAL_REQUEST -> "/approvals/" + entityId;
            case NotificationEntityTypes.DEFECT -> "/defects/" + entityId;
            case NotificationEntityTypes.ACTUAL_COST ->
                    "/financial-review?reviewQueue=true&actualCostId=" + entityId;
            case NotificationEntityTypes.MAINTENANCE_DUE_EVENT ->
                    "/maintenance/due-events?eventId=" + entityId;
            case NotificationEntityTypes.REPAIR_CAMPAIGN -> "/repair-campaigns/" + entityId;
            case NotificationEntityTypes.PLANNED_SHUTDOWN -> "/planned-shutdowns/" + entityId;
            case NotificationEntityTypes.MAINTENANCE_BUDGET -> "/budgets?budgetId=" + entityId;
            case NotificationEntityTypes.EQUIPMENT -> "/equipment/" + entityId;
            default -> null;
        };
    }
}
