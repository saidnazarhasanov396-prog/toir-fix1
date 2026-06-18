package com.toir.service;

import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.enums.EquipmentUsageSessionStatus;
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

    static final String ENTITY_TYPE = "EquipmentUsageSession";
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
            String message = overdueMessage(session, now);
            String entityId = session.getId() == null ? null : session.getId().toString();
            notificationService.notifyEmployee(
                    session.getOperatorEmployeeId(),
                    TITLE,
                    message,
                    NotificationSeverity.WARNING,
                    ENTITY_TYPE,
                    entityId
            );
            UUID assignmentActorUserId = session.getAssignmentActorUserId();
            if (assignmentActorUserId != null) {
                notificationService.notifyUser(
                        assignmentActorUserId,
                        TITLE,
                        message,
                        NotificationSeverity.WARNING,
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

    private String overdueMessage(EquipmentUsageSession session, Instant now) {
        return "Siz transportni %s. Iltimos, transportni qaytaring."
                .formatted(formatLateDuration(Duration.between(session.getDueAt(), now)));
    }

    private String formatLateDuration(Duration lateDuration) {
        long totalMinutes = Math.max(1L, lateDuration.toMinutes());
        long days = totalMinutes / 1_440L;
        long hours = (totalMinutes % 1_440L) / 60L;
        long minutes = totalMinutes % 60L;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + " kun");
        }
        if (hours > 0) {
            parts.add(hours + " soat");
        }
        if (parts.isEmpty()) {
            parts.add(minutes + " daqiqa");
        }
        return String.join(" ", parts) + "ga kechikdingiz";
    }
}
