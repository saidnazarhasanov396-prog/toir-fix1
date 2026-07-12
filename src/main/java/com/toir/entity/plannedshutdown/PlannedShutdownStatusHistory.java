package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import com.toir.enums.PlannedShutdownStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_status_history")
@Getter
@Setter
public class PlannedShutdownStatusHistory extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false)
    private UUID plannedShutdownId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status")
    private PlannedShutdownStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false)
    private PlannedShutdownStatus toStatus;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(columnDefinition = "text")
    private String reason;
    @Column(name = "old_effective_start_at") private Instant oldEffectiveStartAt;
    @Column(name = "old_effective_end_at") private Instant oldEffectiveEndAt;
    @Column(name = "new_effective_start_at") private Instant newEffectiveStartAt;
    @Column(name = "new_effective_end_at") private Instant newEffectiveEndAt;
    @Column(name = "scope_version") private Long scopeVersion;
    @Column(name = "correlation_key") private String correlationKey;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
}
