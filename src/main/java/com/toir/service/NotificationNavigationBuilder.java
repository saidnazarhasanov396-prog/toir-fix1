package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.enums.NotificationEventType;
import com.toir.repository.PprPlanRepository;
import com.toir.service.pprcalendar.PprOperationalCalendarPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NotificationNavigationBuilder {

    static final String METADATA_ROUTE_CONTEXT = "routeContext";
    static final String ROUTE_CONTEXT_OPERATIONAL = "operational";
    static final String ROUTE_CONTEXT_BUILDER = "builder";

    private final PprPlanRepository planRepository;
    private final PprOperationalCalendarPolicy operationalCalendarPolicy;

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
        Map<String, Object> metadata = new LinkedHashMap<>();
        String actionUrl = route(canonicalType, normalizedId, metadata);
        return new NotificationNavigation(
                eventType == null ? null : eventType.name(),
                canonicalType,
                normalizedId,
                actionUrl,
                Map.copyOf(metadata)
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
            metadata.put(METADATA_ROUTE_CONTEXT, routeContextForPlan(planId));
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
                    route(NotificationEntityTypes.WORK_ORDER, workOrderId.toString(), new LinkedHashMap<>())
            )));
        }
        String actionUrl = taskId != null && planId != null
                ? pprPlanDetailRoute(planId, taskId)
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
        Map<String, Object> metadata = new LinkedHashMap<>(owner.metadata());
        if (approvalRequestId != null) {
            metadata.put("approvalRequestId", approvalRequestId.toString());
            metadata.put("secondaryActions", List.of(secondaryAction(
                    "notifications.actions.openApprovalHistory",
                    NotificationEntityTypes.APPROVAL_REQUEST,
                    approvalRequestId.toString(),
                    route(NotificationEntityTypes.APPROVAL_REQUEST, approvalRequestId.toString(), new LinkedHashMap<>())
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

    public NotificationNavigation forSparePartDue(
            NotificationEventType eventType,
            UUID equipmentId,
            UUID dueEventId,
            UUID installationId
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("equipmentId", equipmentId.toString());
        metadata.put("eventId", dueEventId.toString());
        metadata.put("installationId", installationId.toString());
        String actionUrl = "/equipment/" + equipmentId
                + "?tab=spareParts&view=attention&eventId=" + dueEventId
                + "&installationId=" + installationId;
        return new NotificationNavigation(
                eventType == null ? null : eventType.name(),
                NotificationEntityTypes.SPARE_PART_DUE_EVENT,
                dueEventId.toString(),
                actionUrl,
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

    private String route(String entityType, String entityId, Map<String, Object> metadata) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return null;
        }
        return switch (entityType) {
            case NotificationEntityTypes.WORK_ORDER -> "/work-orders/" + entityId;
            case NotificationEntityTypes.REPAIR_REQUEST -> "/repair-requests/" + entityId;
            case NotificationEntityTypes.PPR_PLAN -> pprPlanRoute(entityId, metadata);
            case NotificationEntityTypes.PPR_PLANNING_SESSION ->
                    "/maintenance-schedule-builder/sessions/" + entityId;
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

    private String pprPlanRoute(String entityId, Map<String, Object> metadata) {
        UUID planId = parseUuid(entityId);
        if (planId == null) {
            return null;
        }
        metadata.put(METADATA_ROUTE_CONTEXT, routeContextForPlan(planId));
        return pprPlanDetailRoute(planId, null);
    }

    private String pprPlanDetailRoute(UUID planId, UUID taskId) {
        if (planId == null) {
            return "/ppr-calendar?view=table";
        }
        boolean operational = isOperationalPlan(planId);
        String base = operational
                ? "/ppr-calendar/" + planId
                : "/maintenance-schedule-builder/" + planId;
        if (taskId == null) {
            return operational ? base : base + "?draft=1";
        }
        return operational
                ? base + "?taskId=" + taskId
                : base + "?taskId=" + taskId + "&draft=1";
    }

    private String routeContextForPlan(UUID planId) {
        return isOperationalPlan(planId) ? ROUTE_CONTEXT_OPERATIONAL : ROUTE_CONTEXT_BUILDER;
    }

    private boolean isOperationalPlan(UUID planId) {
        return planRepository.findByIdAndIsDeletedFalse(planId)
                .map(operationalCalendarPolicy::includes)
                .orElse(false);
    }

    private static UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
