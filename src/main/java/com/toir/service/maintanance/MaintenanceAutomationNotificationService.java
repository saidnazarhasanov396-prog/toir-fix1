package com.toir.service.maintanance;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.security.PermissionConstants;
import com.toir.service.NotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaintenanceAutomationNotificationService {

    public static final String ENTITY_TYPE = "MaintenanceDueEvent";
    public static final String MAINTENANCE_UPCOMING = "MAINTENANCE_UPCOMING";
    public static final String MAINTENANCE_DUE = "MAINTENANCE_DUE";
    public static final String MAINTENANCE_OVERDUE = "MAINTENANCE_OVERDUE";
    public static final String MAINTENANCE_BLOCKED = "MAINTENANCE_BLOCKED";
    public static final String MAINTENANCE_WORK_ORDER_CREATED = "MAINTENANCE_WORK_ORDER_CREATED";
    public static final String MAINTENANCE_REQUIRES_APPROVAL = "MAINTENANCE_REQUIRES_APPROVAL";

    private final NotificationService notificationService;

    public int notifyEventStatus(MaintenanceDueEvent event,
                                 EquipmentMaintenanceEffectiveRule rule,
                                 Equipment equipment) {
        if (event == null || event.getId() == null || event.getDueStatus() == null) {
            return 0;
        }
        return notifyRecipients(
                event,
                rule,
                equipment,
                statusTitle(event.getDueStatus()),
                statusMessage(event.getDueStatus(), event.getCycleKey()),
                statusSeverity(event.getDueStatus())
        );
    }

    public int notifyRequiresApproval(MaintenanceDueEvent event,
                                      EquipmentMaintenanceEffectiveRule rule,
                                      Equipment equipment) {
        return notifyRecipients(
                event,
                rule,
                equipment,
                MAINTENANCE_REQUIRES_APPROVAL,
                "Maintenance requires approval: " + event.getCycleKey(),
                NotificationSeverity.WARNING
        );
    }

    public int notifyWorkOrderCreated(MaintenanceDueEvent event,
                                      EquipmentMaintenanceEffectiveRule rule,
                                      Equipment equipment) {
        return notifyRecipients(
                event,
                rule,
                equipment,
                MAINTENANCE_WORK_ORDER_CREATED,
                "Maintenance work order created: " + event.getCycleKey(),
                NotificationSeverity.INFO
        );
    }

    private int notifyRecipients(MaintenanceDueEvent event,
                                 EquipmentMaintenanceEffectiveRule rule,
                                 Equipment equipment,
                                 String title,
                                 String message,
                                 NotificationSeverity severity) {
        if (event == null || event.getId() == null || rule == null || equipment == null) {
            return 0;
        }
        String entityId = event.getId().toString();
        if (rule.defaultResponsibleId() != null) {
            return notificationService.notifyEmployee(
                    rule.defaultResponsibleId(),
                    title,
                    message,
                    severity,
                    ENTITY_TYPE,
                    entityId
            ).isPresent() ? 1 : 0;
        }

        UUID departmentId = effectiveDepartmentId(rule, equipment);
        List<?> departmentNotifications = notificationService.notifyDepartmentByPermission(
                departmentId,
                PermissionConstants.MAINTENANCE_EVENT_READ,
                title,
                message,
                severity,
                ENTITY_TYPE,
                entityId
        );
        if (!departmentNotifications.isEmpty()) {
            return departmentNotifications.size();
        }

        if (equipment.getResponsibleId() == null) {
            return 0;
        }
        return notificationService.notifyEmployee(
                equipment.getResponsibleId(),
                title,
                message,
                severity,
                ENTITY_TYPE,
                entityId
        ).isPresent() ? 1 : 0;
    }

    private UUID effectiveDepartmentId(EquipmentMaintenanceEffectiveRule rule, Equipment equipment) {
        if (rule.defaultDepartmentId() != null) {
            return rule.defaultDepartmentId();
        }
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }

    private String statusTitle(MaintenanceDueStatus status) {
        return switch (status) {
            case UPCOMING -> MAINTENANCE_UPCOMING;
            case DUE -> MAINTENANCE_DUE;
            case OVERDUE -> MAINTENANCE_OVERDUE;
            case BLOCKED -> MAINTENANCE_BLOCKED;
            case NOT_DUE -> MAINTENANCE_DUE;
        };
    }

    private String statusMessage(MaintenanceDueStatus status, String cycleKey) {
        String cycle = cycleKey == null ? "" : cycleKey;
        return switch (status) {
            case UPCOMING -> "Maintenance upcoming: " + cycle;
            case DUE, NOT_DUE -> "Maintenance due: " + cycle;
            case OVERDUE -> "Maintenance overdue: " + cycle;
            case BLOCKED -> "Maintenance blocked: " + cycle;
        };
    }

    private NotificationSeverity statusSeverity(MaintenanceDueStatus status) {
        return switch (status) {
            case OVERDUE, BLOCKED -> NotificationSeverity.CRITICAL;
            case DUE -> NotificationSeverity.WARNING;
            case UPCOMING, NOT_DUE -> NotificationSeverity.INFO;
        };
    }
}
