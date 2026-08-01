package com.toir.dto.notificationdevice;

import com.toir.entity.UserFcmToken;
import com.toir.enums.FcmDevicePlatform;
import java.time.Instant;
import java.util.UUID;

public record NotificationDeviceDto(
        UUID id,
        UUID userId,
        String token,
        FcmDevicePlatform platform,
        String deviceId,
        String languageCode,
        boolean active,
        Instant lastSeenAt,
        Instant createdAt,
        Instant updatedAt
) {
    public NotificationDeviceDto(
            UUID id, UUID userId, String token, FcmDevicePlatform platform, String deviceId,
            boolean active, Instant lastSeenAt, Instant createdAt, Instant updatedAt
    ) {
        this(id, userId, token, platform, deviceId, "ru", active, lastSeenAt, createdAt, updatedAt);
    }

    public static NotificationDeviceDto from(UserFcmToken token) {
        return new NotificationDeviceDto(
                token.getId(),
                token.getUserId(),
                token.getToken(),
                token.getPlatform(),
                token.getDeviceId(),
                token.getLanguageCode(),
                token.isActive(),
                token.getLastSeenAt(),
                token.getCreatedAt(),
                token.getUpdatedAt()
        );
    }
}
