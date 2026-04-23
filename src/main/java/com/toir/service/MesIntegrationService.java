package com.toir.service;

import com.toir.dto.integration.ConnectionTestResult;
import com.toir.dto.integration.IntegrationSyncLogDto;
import com.toir.entity.IntegrationEndpoint;
import com.toir.entity.IntegrationSyncLog;
import com.toir.entity.IntegrationSyncStatus;
import com.toir.exception.RestException;
import com.toir.repository.IntegrationEndpointRepository;
import com.toir.repository.IntegrationSyncLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MesIntegrationService {

    private final IntegrationEndpointRepository endpointRepository;
    private final IntegrationSyncLogRepository syncLogRepository;

    public MesIntegrationService(IntegrationEndpointRepository endpointRepository,
                                 IntegrationSyncLogRepository syncLogRepository) {
        this.endpointRepository = endpointRepository;
        this.syncLogRepository = syncLogRepository;
    }

    public ConnectionTestResult testConnection(UUID endpointId) {
        IntegrationEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> RestException.notFound("Endpoint not found: " + endpointId));
        String fullUrl = ep.getFullUrl();
        int timeout = ep.getTimeoutSeconds() != null ? ep.getTimeoutSeconds() : 10;

        long start = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout))
                    .build();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl.endsWith("/") ? fullUrl : fullUrl + "/"))
                    .timeout(Duration.ofSeconds(timeout))
                    .GET();

            if (ep.getApiKey() != null && !ep.getApiKey().isBlank()) {
                reqBuilder.header("X-API-Key", ep.getApiKey());
            }
            if (ep.getUsername() != null && ep.getPassword() != null) {
                String creds = java.util.Base64.getEncoder().encodeToString(
                        (ep.getUsername() + ":" + ep.getPassword()).getBytes());
                reqBuilder.header("Authorization", "Basic " + creds);
            }

            HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            String serverInfo = response.headers().firstValue("Server").orElse(null);
            return new ConnectionTestResult(true, response.statusCode(), elapsed, serverInfo, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return new ConnectionTestResult(false, 0, elapsed, null, e.getMessage());
        }
    }

    public IntegrationSyncLogDto syncModule(UUID endpointId, String module) {
        IntegrationEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> RestException.notFound("Endpoint not found: " + endpointId));

        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setEndpointId(endpointId);
        log.setModule(module);
        log.setDirection("BIDIRECTIONAL");
        log.setStartedAt(Instant.now());
        log.setStatus(IntegrationSyncStatus.RUNNING);
        syncLogRepository.save(log);

        try {
            String fullUrl = ep.getFullUrl();
            int timeout = ep.getTimeoutSeconds() != null ? ep.getTimeoutSeconds() : 30;
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout)).build();

            String path = switch (module) {
                case "WORK_ORDERS" -> "/integrations/eam/work-orders";
                case "DOWNTIMES" -> "/downtimes";
                case "DEFECTS" -> "/quality/defects";
                case "SCADA" -> "/integrations/scada/ingest-log";
                case "PRODUCTION" -> "/production/orders";
                default -> throw RestException.badRequest("Unknown module: " + module);
            };

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl + path))
                    .timeout(Duration.ofSeconds(timeout))
                    .GET();

            if (ep.getApiKey() != null && !ep.getApiKey().isBlank()) {
                reqBuilder.header("X-API-Key", ep.getApiKey());
            }

            HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            log.setFinishedAt(Instant.now());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.setStatus(IntegrationSyncStatus.SUCCESS);
                log.setRecordsReceived(countRecords(response.body()));
                ep.setLastSyncAt(Instant.now());
                ep.setLastSyncStatus(IntegrationSyncStatus.SUCCESS);
                ep.setLastError(null);
            } else {
                log.setStatus(IntegrationSyncStatus.FAILED);
                log.setErrorMessage("HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(500, response.body().length())));
                ep.setLastSyncAt(Instant.now());
                ep.setLastSyncStatus(IntegrationSyncStatus.FAILED);
                ep.setLastError(log.getErrorMessage());
            }
        } catch (Exception e) {
            log.setFinishedAt(Instant.now());
            log.setStatus(IntegrationSyncStatus.FAILED);
            log.setErrorMessage(e.getMessage());
            ep.setLastSyncAt(Instant.now());
            ep.setLastSyncStatus(IntegrationSyncStatus.FAILED);
            ep.setLastError(e.getMessage());
        }

        return IntegrationSyncLogDto.from(log);
    }

    @Transactional(readOnly = true)
    public List<IntegrationSyncLogDto> getLogs(UUID endpointId) {
        if (endpointId != null) {
            return syncLogRepository.findTop50ByEndpointIdOrderByStartedAtDesc(endpointId).stream()
                    .map(IntegrationSyncLogDto::from).toList();
        }
        return syncLogRepository.findTop100ByOrderByStartedAtDesc().stream()
                .map(IntegrationSyncLogDto::from).toList();
    }

    private int countRecords(String jsonBody) {
        if (jsonBody == null) return 0;
        long count = jsonBody.chars().filter(c -> c == '{').count();
        return (int) Math.max(count - 1, 0);
    }
}
