package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_closure_snapshots")
@Immutable
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "isDeleted", column = @Column(name = "is_deleted", nullable = false, updatable = false))
})
@Getter @Setter
public class PlannedShutdownClosureSnapshot extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false, updatable = false) private UUID plannedShutdownId;
    @Column(name = "scope_version", nullable = false, updatable = false) private Long scopeVersion;
    @Column(name = "window_version", nullable = false, updatable = false) private Long windowVersion;
    @Column(name = "closed_by_id", nullable = false, updatable = false) private UUID closedById;
    @Column(name = "closed_at", nullable = false, updatable = false) private Instant closedAt;
    @Column(name = "snapshot_hash", nullable = false, length = 64, updatable = false) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text", updatable = false) private String snapshotJson;
}
