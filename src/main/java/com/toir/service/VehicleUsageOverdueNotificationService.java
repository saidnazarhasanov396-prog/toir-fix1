package com.toir.service;

import com.toir.dto.notification.NotificationContent;
import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.enums.EquipmentUsageSessionStatus;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.EquipmentUsageSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleUsageOverdueNotificationService {

    static final String ENTITY_TYPE = NotificationEntityTypes.EQUIPMENT_USAGE_SESSION;
    static final String TITLE = "Vehicle return overdue";

    private final EquipmentUsageSessionRepository sessionRepository;
    private final NotificationService notificationService;

    @Transactional
    public int notifyOverdueSessions() {
        Instant now = Instant.now();
        List<EquipmentUsageSession> sessions = sessionRepository.findOpenOverduePendingNotification(now);
        int notified = 0;
        for (EquipmentUsageSession session : sessions) {
            if (!isNotificationCandidate(session, now)) {
                continue;
            }
            NotificationContent content = overdueContent(session, now);
            String entityId = session.getId() == null ? null : session.getId().toString();
            notificationService.notifyEmployee(
                    session.getOperatorEmployeeId(),
                        content,
                    NotificationSeverity.WARNING,
                    NotificationEventType.VEHICLE_RETURN_OVERDUE,
                    ENTITY_TYPE,
                    entityId
            );
            UUID assignmentActorUserId = session.getAssignmentActorUserId();
            if (assignmentActorUserId != null) {
                notificationService.notifyUser(
                        assignmentActorUserId,
                    content,
                        NotificationSeverity.WARNING,
                        NotificationEventType.VEHICLE_RETURN_OVERDUE,
                        ENTITY_TYPE,
                        entityId
                );
            }
            session.setOverdueNotifiedAt(now);
            notified++;
        }
        return notified;
    }

    private boolean isNotificationCandidate(EquipmentUsageSession session, Instant now) {
        return session != null
                && session.getStatus() == EquipmentUsageSessionStatus.OPEN
                && session.getReturnedAt() == null
                && session.getDueAt() != null
                && session.getOverdueNotifiedAt() == null
                && session.getDueAt().isBefore(now);
    }

    private NotificationContent overdueContent(EquipmentUsageSession session, Instant now) {
        Duration lateDuration = Duration.between(session.getDueAt(), now);
        return new NotificationContent(
                "Просрочен возврат транспорта",
                "Вы просрочили возврат транспорта на " + formatLateDuration(lateDuration, "ru")
                        + ". Пожалуйста, верните транспорт.",
                "Transportni qaytarish muddati o‘tdi",
                "Siz transportni " + formatLateDuration(lateDuration, "uz")
                        + " kech qaytaryapsiz. Iltimos, transportni qaytaring.",
                "Vehicle return overdue",
                "The vehicle return is " + formatLateDuration(lateDuration, "en")
                        + " overdue. Please return the vehicle."
        );
    }

    private String formatLateDuration(Duration lateDuration, String language) {
        long totalMinutes = Math.max(1L, lateDuration.toMinutes());
        long days = totalMinutes / 1_440L;
        long hours = (totalMinutes % 1_440L) / 60L;
        long minutes = totalMinutes % 60L;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(switch (language) {
                case "ru" -> days + " дн";
                case "en" -> days + " d";
                default -> days + " kun";
            });
        }
        if (hours > 0) {
            parts.add(switch (language) {
                case "ru" -> hours + " ч";
                case "en" -> hours + " h";
                default -> hours + " soat";
            });
        }
        if (parts.isEmpty()) {
            parts.add(switch (language) {
                case "ru" -> minutes + " мин";
                case "en" -> minutes + " min";
                default -> minutes + " daqiqa";
            });
        }
        return String.join(" ", parts);
    }
}
