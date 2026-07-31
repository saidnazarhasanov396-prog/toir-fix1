package com.toir.dto.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EquipmentLifecycleExportManifestV1(
        String manifestVersion,
        UUID exportJobId,
        String datasetRecordType,
        String schemaVersion,
        String exportFormat,
        String encoding,
        String lineEnding,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant asOf,
        String contextProfile,
        JsonNode resolvedPolicy,
        String requestFingerprint,
        String policyFingerprint,
        EquipmentLifecycleExportScopeMode selectionMode,
        long selectedCount,
        long exportedRecordCount,
        long datasetByteSize,
        List<Artifact> finalizedInputs,
        String contextVersionAlgorithm,
        String orderingRule,
        Map<String, Object> truncationAndDataQualitySummary,
        Consistency consistency,
        Instant expiresAt,
        List<String> warnings
) {
    public EquipmentLifecycleExportManifestV1 {
        finalizedInputs = List.copyOf(finalizedInputs);
        truncationAndDataQualitySummary = Map.copyOf(truncationAndDataQualitySummary);
        warnings = List.copyOf(warnings);
    }

    public record Artifact(String filename, String mediaType, long size, String sha256) {}

    public record Consistency(
            String model,
            boolean equipmentMembershipFrozen,
            boolean singleDatabaseSnapshot,
            String transactionIsolation,
            boolean sourceRowsMayChangeDuringExport,
            String traceability
    ) {}
}
