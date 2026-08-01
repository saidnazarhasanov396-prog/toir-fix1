package com.toir.service.sparepartlifecycle;

import com.toir.dto.notification.NotificationContent;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.PermissionConstants;
import com.toir.service.NotificationService;
import com.toir.service.NotificationNavigationBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SparePartLifecycleNotificationListener {

    private final EquipmentRepository equipmentRepository;
    private final NotificationService notificationService;
    private final NotificationNavigationBuilder navigationBuilder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDueTransition(SparePartDueTransitionEvent event) {
        equipmentRepository.findByIdAndIsDeletedFalse(event.equipmentId()).ifPresent(equipment -> {
            try {
                notificationService.notifyDepartmentByPermission(
                        equipment.getResponsibleDepartmentId() == null
                                ? equipment.getDepartmentId()
                                : equipment.getResponsibleDepartmentId(),
                        PermissionConstants.SPARE_PART_DUE_READ,
                        new NotificationContent(
                                "Ресурс установленной запчасти: " + event.state(),
                                "Установка " + event.installationId() + " требует действия " + event.action() + ".",
                                "O‘rnatilgan ehtiyot qism resursi: " + event.state(),
                                event.installationId() + " o‘rnatmasi " + event.action() + " amalini talab qiladi.",
                                "Installed spare-part service life: " + event.state(),
                                "Installation " + event.installationId() + " requires action " + event.action() + "."
                        ),
                        severity(event),
                        navigationBuilder.forSparePartDue(
                                NotificationEventType.SPARE_PART_DUE,
                                event.equipmentId(),
                                event.dueEventId(),
                                event.installationId())
                );
            } catch (RuntimeException exception) {
                log.error("spare_part_due_notification_failed dueEventId={}", event.dueEventId(), exception);
            }
        });
    }

    private static NotificationSeverity severity(SparePartDueTransitionEvent event) {
        return switch (event.state()) {
            case OVERDUE -> NotificationSeverity.CRITICAL;
            case DUE -> NotificationSeverity.WARNING;
            case WARNING, UPCOMING, RESOLVED -> NotificationSeverity.INFO;
        };
    }
}
