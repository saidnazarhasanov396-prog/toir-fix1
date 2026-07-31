package com.toir.entity.equipmentlifecycleexport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_lifecycle_export_parts")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentLifecycleExportPart {
    @Id
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "part_number", nullable = false)
    private int partNumber;
    @Column(name = "first_ordinal", nullable = false)
    private long firstOrdinal;
    @Column(name = "last_ordinal", nullable = false)
    private long lastOrdinal;
    @Column(name = "record_count", nullable = false)
    private int recordCount;
    @Column(name = "object_key", nullable = false, columnDefinition = "text")
    private String objectKey;
    @Column(name = "object_size", nullable = false)
    private long objectSize;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "fencing_token", nullable = false)
    private UUID fencingToken;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
