package com.toir.entity.equipmentlifecycleexport;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_lifecycle_export_artifacts")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentLifecycleExportArtifact {
    @Id
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 32)
    private EquipmentLifecycleExportArtifactType artifactType;
    @Column(nullable = false, length = 160)
    private String filename;
    @Column(name = "media_type", nullable = false, length = 120)
    private String mediaType;
    @Column(name = "object_key", nullable = false, columnDefinition = "text")
    private String objectKey;
    @Column(name = "object_size", nullable = false)
    private long objectSize;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
