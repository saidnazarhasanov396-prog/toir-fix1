package com.toir.service;

import com.toir.dto.integration.ConnectionTestResult;
import com.toir.dto.integration.IntegrationSyncLogDto;
import com.toir.entity.IntegrationEndpoint;
import com.toir.entity.IntegrationSyncLog;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.exception.RestException;
import com.toir.repository.IntegrationEndpointRepository;
import com.toir.repository.IntegrationSyncLogRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class MesIntegrationService {

    private final IntegrationEndpointRepository endpointRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    public ConnectionTestResult testConnection(UUID endpointId) {
        IntegrationEndpoint ep = endpointRepository.findByIdAndIsDeletedFalse(endpointId)
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
        IntegrationEndpoint ep = endpointRepository.findByIdAndIsDeletedFalse(endpointId)
                .orElseThrow(() -> RestException.notFound("Endpoint not found: " + endpointId));

        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setEndpointId(endpointId);
        log.setModule(module);
        log.setDirection("BIDIRECTIONAL");
        log.setStartedAt(Instant.now());
        log.setStatus(IntegrationSyncStatus.RUNNING);
        IntegrationSyncLog savedLog = syncLogRepository.save(log);
        auditSyncLog(AuditAction.CREATE, savedLog.getId(), null, savedLog);
        String oldLogJson = auditSerializationService.toJson(savedLog);
        String oldEndpointJson = auditSerializationService.toJson(ep);

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
        auditSyncLog(AuditAction.UPDATE, log.getId(), oldLogJson, log);
        auditEndpoint(AuditAction.UPDATE, ep.getId(), oldEndpointJson, ep);

        return IntegrationSyncLogDto.from(log);
    }

    @Transactional(readOnly = true)
    public List<IntegrationSyncLogDto> getLogs(UUID endpointId) {
        if (endpointId != null) {
            return syncLogRepository.findTop50ByEndpointIdAndIsDeletedFalseOrderByStartedAtDesc(endpointId).stream()
                    .map(IntegrationSyncLogDto::from).toList();
        }
        return syncLogRepository.findTop100ByIsDeletedFalseOrderByStartedAtDesc().stream()
                .map(IntegrationSyncLogDto::from).toList();
    }

    private int countRecords(String jsonBody) {
        if (jsonBody == null) return 0;
        long count = jsonBody.chars().filter(c -> c == '{').count();
        return (int) Math.max(count - 1, 0);
    }

    private void auditSyncLog(AuditAction action, UUID id, String oldJson, IntegrationSyncLog current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "integration_sync_log",
                id != null ? id.toString() : null,
                action,
                AuditModule.INTEGRATION_SYNC_LOG,
                auditSyncLogMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditEndpoint(AuditAction action, UUID id, String oldJson, IntegrationEndpoint current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "integration_endpoint",
                id != null ? id.toString() : null,
                action,
                AuditModule.INTEGRATION_ENDPOINT,
                auditEndpointMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditSyncLogMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Лог синхронизации интеграции создан";
            case UPDATE -> "Лог синхронизации интеграции обновлен";
            case DELETE -> "Лог синхронизации интеграции удален";
            default -> "Действие выполнено над логом синхронизации интеграции";
        };
    }

    private String auditEndpointMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Интеграционная точка создана";
            case UPDATE -> "Интеграционная точка обновлена";
            case DELETE -> "Интеграционная точка удалена";
            default -> "Действие выполнено над интеграционной точкой";
        };
    }
}
