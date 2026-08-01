package com.toir.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.toir.config.FirebaseProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        FirebaseProperties properties = new FirebaseProperties();
        properties.setEnabled(true);
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessagingProvider, tokenRepository, properties);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(null);

        sender.sendToUser(notification(UUID.randomUUID()));

        verify(tokenRepository, never()).findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sendToUserLoadsActiveTokensWhenFirebaseIsConfigured() {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessagingProvider, tokenRepository, new FirebaseProperties());
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

    @Test
    void sendToUserBuildsAndroidMessageWithNotificationPayload() throws Exception {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(
                firebaseMessagingProvider, tokenRepository, new FirebaseProperties());
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        UUID userId = UUID.randomUUID();

        UserFcmToken token = new UserFcmToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setToken("android-token-1");
        token.setPlatform(FcmDevicePlatform.ANDROID);
        token.setLastSeenAt(Instant.now());

        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
        when(tokenRepository.findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(userId))
                .thenReturn(List.of(token));
        when(messaging.send(org.mockito.ArgumentMatchers.any(Message.class)))
                .thenReturn("projects/test/messages/android-msg-1");

        sender.sendToUser(notification(userId));

        verify(messaging).send(org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void sendToUserBuildsIosMessageWithNotificationPayload() throws Exception {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(
                firebaseMessagingProvider, tokenRepository, new FirebaseProperties());
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        UUID userId = UUID.randomUUID();

        UserFcmToken token = new UserFcmToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setToken("ios-token-1");
        token.setPlatform(FcmDevicePlatform.IOS);
        token.setLastSeenAt(Instant.now());

        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
        when(tokenRepository.findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(userId))
                .thenReturn(List.of(token));
        when(messaging.send(org.mockito.ArgumentMatchers.any(Message.class)))
                .thenReturn("projects/test/messages/ios-msg-1");

        sender.sendToUser(notification(userId));

        verify(messaging).send(org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void sendToUserBuildsWebMessageWithNotificationPayload() throws Exception {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(
                firebaseMessagingProvider, tokenRepository, new FirebaseProperties());
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        UUID userId = UUID.randomUUID();

        UserFcmToken token = new UserFcmToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setToken("web-token-1");
        token.setPlatform(FcmDevicePlatform.WEB);
        token.setLastSeenAt(Instant.now());

        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
        when(tokenRepository.findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(userId))
                .thenReturn(List.of(token));
        when(messaging.send(org.mockito.ArgumentMatchers.any(Message.class)))
                .thenReturn("projects/test/messages/web-msg-1");

        sender.sendToUser(notification(userId));

        verify(messaging).send(org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void payloadUsesTokenLanguageAndCarriesAllTranslations() {
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(
                firebaseMessagingProvider, tokenRepository, new FirebaseProperties());
        NotificationDto notification = new NotificationDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Русский заголовок", "Русский текст",
                NotificationChannel.WEB, NotificationStatus.SENT, NotificationSeverity.INFO,
                "WORK_ORDER", UUID.randomUUID().toString(), null, null, java.util.Map.of(),
                null, Instant.parse("2026-06-15T05:00:00Z"), null, null, null,
                "O‘zbekcha sarlavha", "O‘zbekcha matn", "English title", "English body"
        );

        var payload = sender.payload(notification, "uz-UZ");

        assertThat(payload)
                .containsEntry("title", "O‘zbekcha sarlavha")
                .containsEntry("body", "O‘zbekcha matn")
                .containsEntry("titleRu", "Русский заголовок")
                .containsEntry("messageRu", "Русский текст")
                .containsEntry("titleEn", "English title")
                .containsEntry("messageEn", "English body");
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
