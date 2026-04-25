package com.toir.service;
import com.toir.entity.WebhookEventLog;
import com.toir.repository.WebhookEventLogRepository;
import com.toir.entity.WebhookSubscription;
import com.toir.repository.WebhookSubscriptionRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Публикация доменных событий в webhook-подписки. Сервис синхронно пытается
 * отправить JSON POST каждому активному подписчику, чей список {@code events}
 * содержит переданный код события, и пишет итог в {@link WebhookEventLog}.
 * Таймаут 5 секунд — интеграции не должны блокировать основной flow.
 */
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookEventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();


    public List<WebhookSubscription> findAll() {
        return subscriptionRepository.findAllByIsDeletedFalse();
    }

    public WebhookSubscription create(WebhookSubscription sub) {
        if (subscriptionRepository.existsByCodeAndIsDeletedFalse(sub.getCode())) {
            throw RestException.conflict("Webhook code already exists: " + sub.getCode());
        }
        return subscriptionRepository.save(sub);
    }

    public WebhookSubscription update(UUID id, WebhookSubscription patch) {
        WebhookSubscription existing = subscriptionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Webhook subscription not found: " + id));
        existing.setName(patch.getName());
        existing.setTargetUrl(patch.getTargetUrl());
        existing.setSecret(patch.getSecret());
        existing.setEvents(patch.getEvents());
        existing.setActive(patch.isActive());
        return existing;
    }

    public void delete(UUID id) {
        var entity = subscriptionRepository.findByIdAndIsDeletedFalse(id).orElseThrow();
        entity.setDeleted(true);
        subscriptionRepository.save(entity);
    }

    /** Публикует событие всем активным подписчикам. Исключения в сети не бросаем — пишем в лог. */
    public int publish(String eventCode, Object payload) {
        List<WebhookSubscription> subs = subscriptionRepository.findAllByActiveTrueAndIsDeletedFalse();
        if (subs.isEmpty()) return 0;

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "event", eventCode,
                    "timestamp", Instant.now().toString(),
                    "payload", payload
            ));
        } catch (JsonProcessingException e) {
            return 0;
        }

        int delivered = 0;
        for (WebhookSubscription sub : subs) {
            if (sub.getEvents() == null || !sub.getEvents().contains(eventCode)) continue;
            WebhookEventLog log = new WebhookEventLog();
            log.setSubscriptionId(sub.getId());
            log.setEventCode(eventCode);
            log.setPayload(body);
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(sub.getTargetUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-Toir-Event", eventCode);
                if (sub.getSecret() != null) {
                    req.header("X-Toir-Signature", sign(body, sub.getSecret()));
                }
                HttpRequest request = req.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.setHttpStatus(resp.statusCode());
                sub.setLastDeliveryStatus(resp.statusCode() + "");
                sub.setLastDeliveryAt(Instant.now());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    sub.setFailureCount(0);
                    delivered++;
                } else {
                    sub.setFailureCount(sub.getFailureCount() + 1);
                }
            } catch (Exception ex) {
                log.setError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                sub.setLastDeliveryStatus("ERROR");
                sub.setLastDeliveryAt(Instant.now());
                sub.setFailureCount(sub.getFailureCount() + 1);
            }
            eventLogRepository.save(log);
        }
        return delivered;
    }

    public List<WebhookEventLog> recentForSubscription(UUID subscriptionId) {
        return eventLogRepository.findTop50BySubscriptionIdAndIsDeletedFalseOrderByFiredAtDesc(subscriptionId);
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            return "";
        }
    }
}
