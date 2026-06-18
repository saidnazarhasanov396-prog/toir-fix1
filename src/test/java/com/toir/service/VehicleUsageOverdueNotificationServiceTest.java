package com.toir.service;

import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.enums.EquipmentUsageSessionStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.EquipmentUsageSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleUsageOverdueNotificationServiceTest {

    @Mock
    EquipmentUsageSessionRepository sessionRepository;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    VehicleUsageOverdueNotificationService service;

    @Test
    void notifyOverdueSessionsNotifiesDriverAndAssignmentActorOnce() {
        UUID sessionId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID assignerId = UUID.randomUUID();
        EquipmentUsageSession session = overdueSession(sessionId, driverId);
        session.setAssignmentActorUserId(assignerId);

        when(sessionRepository.findOpenOverduePendingNotification(any())).thenReturn(List.of(session));

        int notified = service.notifyOverdueSessions();

        assertThat(notified).isEqualTo(1);
        assertThat(session.getOverdueNotifiedAt()).isNotNull();
        verify(notificationService).notifyEmployee(
                eq(driverId),
                eq("Vehicle return overdue"),
                contains("kechikdingiz"),
                eq(NotificationSeverity.WARNING),
                eq("EquipmentUsageSession"),
                eq(sessionId.toString())
        );
        verify(notificationService).notifyUser(
                eq(assignerId),
                eq("Vehicle return overdue"),
                contains("kechikdingiz"),
                eq(NotificationSeverity.WARNING),
                eq("EquipmentUsageSession"),
                eq(sessionId.toString())
        );
    }

    @Test
    void notifyOverdueSessionsStillNotifiesDriverWhenAssignmentActorMissing() {
        UUID sessionId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        EquipmentUsageSession session = overdueSession(sessionId, driverId);

        when(sessionRepository.findOpenOverduePendingNotification(any())).thenReturn(List.of(session));

        int notified = service.notifyOverdueSessions();

        assertThat(notified).isEqualTo(1);
        assertThat(session.getOverdueNotifiedAt()).isNotNull();
        verify(notificationService).notifyEmployee(
                eq(driverId),
                eq("Vehicle return overdue"),
                contains("kechikdingiz"),
                eq(NotificationSeverity.WARNING),
                eq("EquipmentUsageSession"),
                eq(sessionId.toString())
        );
        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any());
    }

    private static EquipmentUsageSession overdueSession(UUID sessionId, UUID driverId) {
        EquipmentUsageSession session = new EquipmentUsageSession();
        session.setId(sessionId);
        session.setEquipmentId(UUID.randomUUID());
        session.setOperatorEmployeeId(driverId);
        session.setStartedAt(Instant.parse("2026-06-17T04:00:00Z"));
        session.setDueAt(Instant.parse("2026-06-17T06:00:00Z"));
        session.setUsageLimitMinutes(120);
        session.setStatus(EquipmentUsageSessionStatus.OPEN);
        return session;
    }
}
