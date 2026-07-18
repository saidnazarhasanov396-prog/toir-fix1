package com.toir.service.integration;

import com.toir.entity.integration.ErpEquipmentStatusOutboxEvent;
import com.toir.repository.integration.ErpEquipmentStatusOutboxRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/** Delivers only allowlisted canonical TOIR events; failed events remain durable for retry. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpEquipmentStatusOutboxDispatcher {
    private final ErpEquipmentStatusOutboxRepository repository;

    @Value("${app.integrations.erp.enabled:false}")
    private boolean enabled;
    @Value("${app.integrations.erp.base-url:}")
    private String baseUrl;
    @Value("${app.integrations.erp.bearer-token:}")
    private String bearerToken;
    @Value("${app.integrations.erp.allowed-hosts:}")
    private String allowedHosts;

    @Scheduled(fixedDelayString = "${app.integrations.erp.outbox-dispatch-delay-ms:15000}")
    @Transactional
    public void dispatch() {
        if (!enabled) {
            return;
        }
        requireAllowedDestination();
        Instant now = Instant.now();
        for (ErpEquipmentStatusOutboxEvent event : repository
                .findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(List.of("PENDING", "RETRY"), now)) {
            try {
                RestClient.create(baseUrl.replaceAll("/+$", ""))
                        .post().uri("/api/integration/envelopes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken.trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(event.getPayload()).retrieve().toBodilessEntity();
                event.setStatus("SENT");
                event.setSentAt(now);
                event.setLastError(null);
            } catch (Exception exception) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(truncate(exception.getMessage()));
                if (attempts >= event.getMaxAttempts()) {
                    event.setStatus("FAILED");
                    event.setNextAttemptAt(null);
                } else {
                    event.setStatus("RETRY");
                    event.setNextAttemptAt(now.plusSeconds(Math.min(3600, 5L << Math.min(attempts, 9))));
                }
                log.warn("TOIR ERP equipment-status event {} will retry: {}", event.getId(), event.getLastError());
            }
            repository.save(event);
        }
    }

    private void requireAllowedDestination() {
        if (baseUrl == null || baseUrl.isBlank() || bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalStateException("ERP delivery URL and token are required");
        }
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        boolean allowedScheme = "https".equalsIgnoreCase(uri.getScheme()) || (local && "http".equalsIgnoreCase(uri.getScheme()));
        Set<String> hosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowedScheme || uri.getUserInfo() != null || host == null || !hosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("ERP destination is not an allowlisted HTTPS host");
        }
    }

    private String truncate(String value) {
        return value == null ? "Delivery failed" : value.substring(0, Math.min(1000, value.length()));
    }
}
