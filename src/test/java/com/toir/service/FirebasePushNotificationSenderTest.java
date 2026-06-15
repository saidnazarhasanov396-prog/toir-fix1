package com.toir.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.toir.dto.notification.NotificationDto;
import com.toir.entity.UserFcmToken;
import com.toir.enums.FcmDevicePlatform;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.repository.UserFcmTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebasePushNotificationSenderTest {

    @Mock
    ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Mock
    UserFcmTokenRepository tokenRepository;

    @Test
    void sendToUserSkipsWhenFirebaseIsNotConfigured() {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessagingProvider, tokenRepository);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(null);

        sender.sendToUser(notification(UUID.randomUUID()));

        verify(tokenRepository, never()).findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sendToUserLoadsActiveTokensWhenFirebaseIsConfigured() {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessagingProvider, tokenRepository);
        FirebaseMessaging messaging = org.mockito.Mockito.mock(FirebaseMessaging.class);
        UUID userId = UUID.randomUUID();
        UserFcmToken token = new UserFcmToken();
        token.setUserId(userId);
        token.setToken("token-1");
        token.setPlatform(FcmDevicePlatform.WEB);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
        when(tokenRepository.findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(userId))
                .thenReturn(List.of(token));

        sender.sendToUser(notification(userId));

        verify(tokenRepository).findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(userId);
    }

    private NotificationDto notification(UUID userId) {
        return new NotificationDto(
                UUID.randomUUID(),
                userId,
                "Title",
                "Body",
                NotificationChannel.WEB,
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "WorkOrder",
                UUID.randomUUID().toString(),
                null,
                Instant.parse("2026-06-15T05:00:00Z")
        );
    }
}
