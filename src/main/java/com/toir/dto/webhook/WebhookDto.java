package com.toir.dto.webhook;

import com.toir.entity.WebhookSubscription;

import java.time.Instant;
import java.util.List;

public record WebhookDto(
         String code,
         String name,
         String lastDeliveryStatus,
         String targetUrl,
         String secret,
         List<String> events,
         boolean active,
         Instant lastDeliveryAt,
         int failureCount
) {

    public static WebhookDto fromEntity(WebhookSubscription w) {
        return new WebhookDto(
                w.getCode(),
                w.getName(),
                w.getLastDeliveryStatus(),
                w.getTargetUrl(),
                w.getSecret(),
                w.getEvents(),
                w.isActive(),
                w.getLastDeliveryAt(),
                w.getFailureCount()
        );
    }
}
