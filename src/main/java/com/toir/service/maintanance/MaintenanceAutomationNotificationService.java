package com.toir.service.maintanance;

import com.toir.dto.notification.NotificationContent;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.security.PermissionConstants;
import com.toir.service.NotificationEntityTypes;
import com.toir.service.NotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaintenanceAutomationNotificationService {

    public static final String ENTITY_TYPE = NotificationEntityTypes.MAINTENANCE_DUE_EVENT;
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
                statusEventType(event.getDueStatus()),
                statusContent(event.getDueStatus(), event.getCycleKey()),
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
                NotificationEventType.MAINTENANCE_REQUIRES_APPROVAL,
                new NotificationContent(
                        "Требуется согласование ТО", "ТО требует согласования: " + event.getCycleKey(),
                        "Texnik xizmatni tasdiqlash kerak", "Texnik xizmatni tasdiqlash kerak: " + event.getCycleKey(),
                        "Maintenance requires approval", "Maintenance requires approval: " + event.getCycleKey()
                ),
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
                NotificationEventType.MAINTENANCE_WORK_ORDER_CREATED,
                new NotificationContent(
                        "Создан заказ-наряд на ТО", "Создан заказ-наряд на ТО: " + event.getCycleKey(),
                        "Texnik xizmat ish buyurtmasi yaratildi", "Texnik xizmat ish buyurtmasi yaratildi: " + event.getCycleKey(),
                        "Maintenance work order created", "Maintenance work order created: " + event.getCycleKey()
                ),
                NotificationSeverity.INFO
        );
    }

    private int notifyRecipients(MaintenanceDueEvent event,
                                 EquipmentMaintenanceEffectiveRule rule,
                                 Equipment equipment,
                                 NotificationEventType eventType,
                                 NotificationContent content,
                                 NotificationSeverity severity) {
        if (event == null || event.getId() == null || rule == null || equipment == null) {
            return 0;
        }
        String entityId = event.getId().toString();
        if (rule.defaultResponsibleId() != null) {
            return notificationService.notifyEmployee(
                    rule.defaultResponsibleId(),
                    content,
                    severity,
                    eventType,
                    ENTITY_TYPE,
                    entityId
            ).isPresent() ? 1 : 0;
        }

        UUID departmentId = effectiveDepartmentId(rule, equipment);
        List<?> departmentNotifications = notificationService.notifyDepartmentByPermission(
                departmentId,
                PermissionConstants.MAINTENANCE_EVENT_READ,
                    content,
                    severity,
                eventType,
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
                    content,
                    severity,
                eventType,
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

    private NotificationEventType statusEventType(MaintenanceDueStatus status) {
        return switch (status) {
            case UPCOMING -> NotificationEventType.MAINTENANCE_UPCOMING;
            case DUE, NOT_DUE -> NotificationEventType.MAINTENANCE_DUE;
            case OVERDUE -> NotificationEventType.MAINTENANCE_OVERDUE;
            case BLOCKED -> NotificationEventType.MAINTENANCE_BLOCKED;
        };
    }

    private NotificationContent statusContent(MaintenanceDueStatus status, String cycleKey) {
        String cycle = cycleKey == null ? "" : cycleKey;
        return switch (status) {
            case UPCOMING -> new NotificationContent(
                    "Приближается ТО", "Приближается срок ТО: " + cycle,
                    "Texnik xizmat yaqinlashmoqda", "Texnik xizmat muddati yaqinlashmoqda: " + cycle,
                    "Maintenance upcoming", "Maintenance upcoming: " + cycle);
            case DUE, NOT_DUE -> new NotificationContent(
                    "Наступил срок ТО", "Наступил срок ТО: " + cycle,
                    "Texnik xizmat muddati keldi", "Texnik xizmat muddati keldi: " + cycle,
                    "Maintenance due", "Maintenance due: " + cycle);
            case OVERDUE -> new NotificationContent(
                    "ТО просрочено", "ТО просрочено: " + cycle,
                    "Texnik xizmat kechikdi", "Texnik xizmat kechikdi: " + cycle,
                    "Maintenance overdue", "Maintenance overdue: " + cycle);
            case BLOCKED -> new NotificationContent(
                    "ТО заблокировано", "ТО заблокировано: " + cycle,
                    "Texnik xizmat bloklandi", "Texnik xizmat bloklandi: " + cycle,
                    "Maintenance blocked", "Maintenance blocked: " + cycle);
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
