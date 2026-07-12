package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_production_returns")
@Getter @Setter
public class PlannedShutdownProductionReturn extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false) private UUID plannedShutdownId;
    @Column(name = "scope_version", nullable = false) private Long scopeVersion;
    @Column(name = "window_version", nullable = false) private Long windowVersion;
    @Column(name = "approved_by_id", nullable = false) private UUID approvedById;
    @Column(name = "approved_at", nullable = false) private Instant approvedAt;
    @Column(nullable = false, columnDefinition = "text") private String evidence;
}
