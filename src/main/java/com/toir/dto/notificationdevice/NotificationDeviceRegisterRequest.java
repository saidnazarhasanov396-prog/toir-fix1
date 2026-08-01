package com.toir.dto.notificationdevice;

import com.toir.enums.FcmDevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationDeviceRegisterRequest(
        @NotBlank String token,
        @NotNull FcmDevicePlatform platform,
        String deviceId,
        String language
) {
    public NotificationDeviceRegisterRequest(String token, FcmDevicePlatform platform, String deviceId) {
        this(token, platform, deviceId, "ru");
    }
}
