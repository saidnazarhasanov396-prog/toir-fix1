package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_closure_snapshots")
@Getter @Setter
public class PlannedShutdownClosureSnapshot extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false) private UUID plannedShutdownId;
    @Column(name = "scope_version", nullable = false) private Long scopeVersion;
    @Column(name = "window_version", nullable = false) private Long windowVersion;
    @Column(name = "closed_by_id", nullable = false) private UUID closedById;
    @Column(name = "closed_at", nullable = false) private Instant closedAt;
    @Column(name = "snapshot_hash", nullable = false, length = 64) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text") private String snapshotJson;
}
