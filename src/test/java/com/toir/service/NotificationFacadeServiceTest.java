package com.toir.service;

import com.toir.dto.notification.NotificationDto;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFacadeServiceTest {

    @Mock
    NotificationService notificationService;

    @Mock
    SlaRuleService slaRuleService;

    @InjectMocks
    NotificationFacadeService service;

    @Test
    void listAppliesSupportedFiltersBeforePagination() {
        UUID recipientId = UUID.randomUUID();
        NotificationDto matching = notification(
                recipientId,
                "Pump repair",
                "WO-1 pump needs repair",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder"
        );
        NotificationDto wrongStatus = notification(
                recipientId,
                "Pump repair",
                "WO-2 pump needs repair",
                NotificationStatus.READ,
                NotificationSeverity.WARNING,
                "WorkOrder"
        );
        NotificationDto wrongEntity = notification(
                recipientId,
                "Pump repair",
                "Cost review",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "ActualCost"
        );

        when(notificationService.findForUser(recipientId))
                .thenReturn(List.of(matching, wrongStatus, wrongEntity));

        var result = service.list(
                recipientId,
                0,
                10,
                "pump",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder",
                false
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(matching);
    }

    @Test
    void listCanFilterUnreadNotifications() {
        UUID recipientId = UUID.randomUUID();
        NotificationDto sent = notification(
                recipientId,
                "Sent",
                "Unread notification",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "WorkOrder"
        );
        NotificationDto read = notification(
                recipientId,
                "Read",
                "Read notification",
                NotificationStatus.READ,
                NotificationSeverity.INFO,
                "WorkOrder"
        );

        when(notificationService.findForUser(recipientId)).thenReturn(List.of(read, sent));

        var result = service.list(recipientId, 0, 10, null, null, null, null, true);

        assertThat(result.getContent()).containsExactly(sent);
    }

    private NotificationDto notification(UUID recipientId, String title, String message,
                                         NotificationStatus status, NotificationSeverity severity,
                                         String entityType) {
        return new NotificationDto(
                UUID.randomUUID(),
                recipientId,
                title,
                message,
                NotificationChannel.WEB,
                status,
                severity,
                entityType,
                UUID.randomUUID().toString(),
                null
        );
    }
}
