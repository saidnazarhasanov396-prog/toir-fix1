package com.toir.dto.equipmentlifecycleexport;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EquipmentLifecycleExportResponses {
    private EquipmentLifecycleExportResponses() {}

    public record JobResponse(
            UUID id,
            EquipmentLifecycleExportStatus status,
            Instant asOf,
            String contextProfile,
            EquipmentLifecycleExportScopeMode selectionMode,
            boolean selectionFrozen,
            long selectedCount,
            long completedCount,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant expiresAt,
            String safeFailureCode,
            String safeFailureSummary,
            UUID failureEquipmentId,
            boolean resumeAllowed,
            String statusUrl,
            List<ArtifactDescriptor> artifacts
    ) {
        public JobResponse {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record ArtifactDescriptor(
            EquipmentLifecycleExportArtifactType type,
            String filename,
            String mediaType,
            long size,
            String sha256
    ) {}
}
