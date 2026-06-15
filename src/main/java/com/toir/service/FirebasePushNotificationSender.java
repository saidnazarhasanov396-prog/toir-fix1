package com.toir.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.toir.dto.notification.NotificationDto;
import com.toir.entity.UserFcmToken;
import com.toir.repository.UserFcmTokenRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    public void sendToUser(NotificationDto notification) {
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (firebaseMessaging == null || notification == null || notification.recipientId() == null) {
            return;
        }

        Map<String, String> data = payload(notification);
        tokenRepository.findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(notification.recipientId())
                .forEach(token -> sendToToken(firebaseMessaging, token, data));
    }

    private void sendToToken(FirebaseMessaging firebaseMessaging, UserFcmToken token, Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setToken(token.getToken())
                    .putAllData(data)
                    .build();
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException ex) {
            handleFirebaseFailure(token, ex);
        } catch (RuntimeException ex) {
            log.warn("Unexpected Firebase push failure for token id {}: {}", token.getId(), ex.getMessage());
        }
    }

    private void handleFirebaseFailure(UserFcmToken token, FirebaseMessagingException ex) {
        MessagingErrorCode errorCode = ex.getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            token.setActive(false);
            tokenRepository.save(token);
            log.info("Deactivated invalid Firebase token id {} after FCM error {}", token.getId(), errorCode);
            return;
        }
        log.warn("Temporary Firebase push failure for token id {} with FCM error {}: {}",
                token.getId(), errorCode, ex.getMessage());
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
