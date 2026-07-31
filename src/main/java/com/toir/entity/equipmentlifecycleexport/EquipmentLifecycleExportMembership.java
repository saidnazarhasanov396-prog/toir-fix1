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
@Table(name = "equipment_lifecycle_export_membership")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentLifecycleExportMembership {
    @Id
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;
    @Column(nullable = false)
    private long ordinal;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
