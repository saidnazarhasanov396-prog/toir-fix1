package com.toir.integration.dto;

import com.toir.integration.IntegrationEndpoint;
import com.toir.integration.IntegrationSyncStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record IntegrationEndpointDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String system,
        @NotBlank String url,
        String authType,
        Boolean active,
        Instant lastSyncAt,
        IntegrationSyncStatus lastSyncStatus
) {
    public static IntegrationEndpointDto from(IntegrationEndpoint e) {
        return new IntegrationEndpointDto(e.getId(), e.getCode(), e.getName(), e.getSystem(), e.getUrl(),
                e.getAuthType(), e.isActive(), e.getLastSyncAt(), e.getLastSyncStatus());
    }
}
