package com.toir.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.toir.config.FirebaseProperties;
import com.toir.dto.notification.NotificationDto;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.repository.UserFcmTokenRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FirebasePushNavigationPayloadTest {

    @Test
    void includesCanonicalNavigationFieldsAndCompatibilityAliases() {
        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        NotificationDto notification = new NotificationDto(
                notificationId,
                recipientId,
                "Assigned",
                "Work order assigned",
                NotificationChannel.WEB,
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                NotificationEntityTypes.WORK_ORDER,
                workOrderId.toString(),
                "WORK_ORDER_ASSIGNED",
                "/work-orders/" + workOrderId,
                Map.of(),
                null
        );
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(
                mock(ObjectProvider.class),
                mock(UserFcmTokenRepository.class),
                new FirebaseProperties()
        );

        Map<String, String> payload = sender.payload(notification);

        assertThat(payload)
                .containsEntry("notificationId", notificationId.toString())
                .containsEntry("eventType", "WORK_ORDER_ASSIGNED")
                .containsEntry("entityType", NotificationEntityTypes.WORK_ORDER)
                .containsEntry("entityId", workOrderId.toString())
                .containsEntry("actionUrl", "/work-orders/" + workOrderId)
                .containsEntry("severity", "WARNING")
                .containsEntry("type", "WARNING")
                .containsEntry("targetType", NotificationEntityTypes.WORK_ORDER)
                .containsEntry("targetId", workOrderId.toString());
    }
}
