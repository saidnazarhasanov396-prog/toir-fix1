package com.toir.dto.notification;

public record NotificationEvaluationResponse(
        int createdNotifications,
        int createdEscalations,
        int resolvedEscalations
) {
}
