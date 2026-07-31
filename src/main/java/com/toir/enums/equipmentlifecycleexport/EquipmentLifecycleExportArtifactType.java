package com.toir.enums.equipmentlifecycleexport;

import lombok.Getter;

@Getter
public enum EquipmentLifecycleExportArtifactType {
    DATASET("equipment-lifecycle-context-v1.ndjson", "application/x-ndjson"),
    SCHEMA("equipment-lifecycle-context-v1.schema.json", "application/schema+json"),
    MANIFEST("equipment-lifecycle-export-manifest-v1.json", "application/json"),
    CHECKSUMS("SHA256SUMS", "text/plain; charset=UTF-8");

    private final String filename;
    private final String mediaType;

    EquipmentLifecycleExportArtifactType(String filename, String mediaType) {
        this.filename = filename;
        this.mediaType = mediaType;
    }
}
