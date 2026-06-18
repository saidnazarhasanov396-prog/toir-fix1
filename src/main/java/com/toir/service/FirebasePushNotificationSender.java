package com.toir.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.toir.config.FirebaseDiagnostics;
import com.toir.config.FirebaseProperties;
import com.toir.dto.notification.NotificationDto;
import com.toir.entity.UserFcmToken;
import com.toir.repository.UserFcmTokenRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebasePushNotificationSender {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final UserFcmTokenRepository tokenRepository;
    private final FirebaseProperties firebaseProperties;

    public void sendToUser(NotificationDto notification) {
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (notification == null) {
            log.warn("Firebase push skipped because notification payload is null");
            return;
        }
        if (notification.recipientId() == null) {
            log.warn("Firebase push skipped for notification {} because recipientId is null", notification.id());
            return;
        }
        if (firebaseMessaging == null) {
            FirebaseDiagnostics.Status status = FirebaseDiagnostics.fromProperties(firebaseProperties);
            if (!status.enabled()) {
                log.info("Firebase push skipped for notification {} recipient {} because Firebase is disabled ({}=false)",
                        notification.id(), notification.recipientId(), FirebaseDiagnostics.ENABLED_KEY);
            } else {
                log.warn("Firebase push skipped for notification {} recipient {} because FirebaseMessaging is not configured; credentialSource={}, credentialsAvailable={}, missingOrInvalidConfigKeys={}",
                        notification.id(),
                        notification.recipientId(),
                        status.credentialSource(),
                        status.credentialsAvailable(),
                        status.missingOrInvalidConfigKeys());
            }
            return;
        }

        Map<String, String> data = payload(notification);
        List<UserFcmToken> tokens = tokenRepository
                .findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(notification.recipientId());
        if (tokens.isEmpty()) {
            log.info("Firebase push skipped for notification {} recipient {} because no active FCM tokens were found",
                    notification.id(), notification.recipientId());
            return;
        }

        log.info("Firebase push sending notification {} to recipient {} using {} active FCM token(s)",
                notification.id(), notification.recipientId(), tokens.size());
        tokens.forEach(token -> sendToToken(firebaseMessaging, token, notification, data));
    }

    private void sendToToken(FirebaseMessaging firebaseMessaging,
                             UserFcmToken token,
                             NotificationDto notification,
                             Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setToken(token.getToken())
                    .putAllData(data)
                    .build();
            String messageId = firebaseMessaging.send(message);
            log.info("Firebase push sent notification {} to recipient {} token id {} messageId={}",
                    notification.id(), notification.recipientId(), token.getId(), messageId);
        } catch (FirebaseMessagingException ex) {
            handleFirebaseFailure(token, notification, ex);
        } catch (RuntimeException ex) {
            log.warn("Unexpected Firebase push failure for notification {} recipient {} token id {}: {}",
                    notification.id(), notification.recipientId(), token.getId(), ex.getMessage());
        }
    }

    private void handleFirebaseFailure(UserFcmToken token, NotificationDto notification, FirebaseMessagingException ex) {
        MessagingErrorCode errorCode = ex.getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            token.setActive(false);
            tokenRepository.save(token);
            log.info("Firebase push failed permanently for notification {} recipient {} token id {}; deactivated token after FCM error {}: {}",
                    notification.id(), notification.recipientId(), token.getId(), errorCode, ex.getMessage());
            return;
        }
        log.warn("Temporary Firebase push failure for notification {} recipient {} token id {} with FCM error {}: {}",
                notification.id(), notification.recipientId(), token.getId(), errorCode, ex.getMessage());
    }

    private Map<String, String> payload(NotificationDto notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", value(notification.title()));
        data.put("body", value(notification.message()));
        data.put("notificationId", notification.id() != null ? notification.id().toString() : "");
        data.put("type", value(notification.severity() != null ? notification.severity().name() : null));
        data.put("targetType", value(notification.entityType()));
        data.put("targetId", value(notification.entityId()));
        data.put("createdAt", notification.createdAt() != null ? notification.createdAt().toString() : Instant.now().toString());
        return data;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
