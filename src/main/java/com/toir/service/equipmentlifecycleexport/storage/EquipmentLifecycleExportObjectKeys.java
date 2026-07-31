package com.toir.service.equipmentlifecycleexport.storage;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;

import java.util.UUID;
import java.util.regex.Pattern;

public final class EquipmentLifecycleExportObjectKeys {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._/-]+");

    private final String prefix;

    public EquipmentLifecycleExportObjectKeys(String configuredPrefix) {
        if (configuredPrefix == null || configuredPrefix.isBlank()) {
            throw new IllegalArgumentException("Export object prefix is required");
        }
        String normalized = configuredPrefix.endsWith("/") ? configuredPrefix : configuredPrefix + "/";
        validateSegments(normalized);
        if (normalized.startsWith("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("Export object prefix must be relative and normalized");
        }
        this.prefix = normalized;
    }

    public String part(UUID jobId, UUID fencingToken, int partNumber, long firstOrdinal, long lastOrdinal) {
        requireIds(jobId, fencingToken);
        if (partNumber < 0 || firstOrdinal < 0 || lastOrdinal < firstOrdinal) {
            throw new IllegalArgumentException("Invalid export part identity");
        }
        return requireManaged(prefix + "staging/" + jobId + "/attempt-" + fencingToken + "/parts/"
                + String.format("part-%08d-ord-%012d-%012d.ndjson", partNumber, firstOrdinal, lastOrdinal));
    }

    public String finalArtifact(
            UUID jobId,
            UUID fencingToken,
            EquipmentLifecycleExportArtifactType artifactType
    ) {
        requireIds(jobId, fencingToken);
        if (artifactType == null) {
            throw new IllegalArgumentException("Artifact type is required");
        }
        return requireManaged(prefix + "final/" + jobId + "/attempt-" + fencingToken + "/"
                + artifactType.getFilename());
    }

    public String stagingPrefix(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("Job ID is required");
        }
        return requireManaged(prefix + "staging/" + jobId + "/");
    }

    public String finalPrefix(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("Job ID is required");
        }
        return requireManaged(prefix + "final/" + jobId + "/");
    }

    public String requireManaged(String key) {
        if (key == null || !key.startsWith(prefix) || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Object key is outside the managed export prefix");
        }
        validateSegments(key);
        return key;
    }

    private static void validateSegments(String value) {
        if (value.startsWith("/") || value.contains("\\") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Object key must be relative and normalized");
        }
        for (String segment : value.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Object key dot segments are forbidden");
            }
        }
    }

    private static void requireIds(UUID jobId, UUID fencingToken) {
        if (jobId == null || fencingToken == null) {
            throw new IllegalArgumentException("Job ID and fencing token are required");
        }
    }
}
