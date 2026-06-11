package com.toir.dto.integration;

import com.toir.entity.IntegrationEndpoint;
import com.toir.enums.IntegrationSyncStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record IntegrationEndpointDto(
        UUID id,
        String code,
        @NotBlank String name,
        @NotBlank String system,
        @NotBlank String url,
        Integer port,
        String basePath,
        String authType,
        String apiKey,
        String username,
        String password,
        Integer timeoutSeconds,
        Integer syncIntervalMinutes,
        boolean syncWorkOrders,
        boolean syncDowntimes,
        boolean syncDefects,
        boolean syncScada,
        boolean syncProduction,
        Boolean active,
        Instant lastSyncAt,
        IntegrationSyncStatus lastSyncStatus,
        String lastError,
        String fullUrl
) {
    public static IntegrationEndpointDto from(IntegrationEndpoint e) {
        return new IntegrationEndpointDto(
                e.getId(), e.getCode(), e.getName(), e.getSystem(), e.getUrl(),
                e.getPort(), e.getBasePath(), e.getAuthType(), e.getApiKey(),
                e.getUsername(), null,
                e.getTimeoutSeconds(), e.getSyncIntervalMinutes(),
                e.isSyncWorkOrders(), e.isSyncDowntimes(), e.isSyncDefects(),
                e.isSyncScada(), e.isSyncProduction(),
                e.isActive(), e.getLastSyncAt(), e.getLastSyncStatus(),
                e.getLastError(), e.getFullUrl());
    }
}
