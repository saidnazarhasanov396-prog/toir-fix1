package com.toir.dto.integration;

import com.toir.entity.IntegrationSyncLog;
import com.toir.entity.IntegrationSyncStatus;

import java.time.Instant;
import java.util.UUID;

public record IntegrationSyncLogDto(
        UUID id,
        UUID endpointId,
        String direction,
        String module,
        Instant startedAt,
        Instant finishedAt,
        IntegrationSyncStatus status,
        Integer recordsSent,
        Integer recordsReceived,
        String errorMessage
) {
    public static IntegrationSyncLogDto from(IntegrationSyncLog l) {
        return new IntegrationSyncLogDto(
                l.getId(), l.getEndpointId(), l.getDirection(), l.getModule(),
                l.getStartedAt(), l.getFinishedAt(), l.getStatus(),
                l.getRecordsSent(), l.getRecordsReceived(), l.getErrorMessage());
    }
}
