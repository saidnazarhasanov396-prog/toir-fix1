package com.toir.dto.notification;

import org.springframework.util.StringUtils;

/**
 * Immutable notification text prepared by the backend in every supported language.
 * Russian remains the legacy/default representation exposed as title/message.
 */
public record NotificationContent(
        String titleRu,
        String messageRu,
        String titleUz,
        String messageUz,
        String titleEn,
        String messageEn
) {
    public NotificationContent {
        requireText(titleRu, "titleRu");
        requireText(messageRu, "messageRu");
        requireText(titleUz, "titleUz");
        requireText(messageUz, "messageUz");
        requireText(titleEn, "titleEn");
        requireText(messageEn, "messageEn");
    }

    public static NotificationContent same(String title, String message) {
        return new NotificationContent(title, message, title, message, title, message);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
