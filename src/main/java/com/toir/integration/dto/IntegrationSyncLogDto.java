package com.toir.integration.dto;

import com.toir.integration.IntegrationSyncStatus;

import java.time.Instant;
import java.util.UUID;

public record IntegrationSyncLogDto(
        UUID endpointId,
        String code,
        String name,
        String system,
        String url,
        Instant syncedAt,
        IntegrationSyncStatus status,
        String message
) {}
