package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownReadinessSeverity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_readiness_items")
@Getter @Setter
public class PlannedShutdownReadinessItem extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false) private UUID plannedShutdownId;
    @Column(name = "readiness_key", nullable = false, length = 128) private String readinessKey;
    @Column(name = "source_type", length = 64) private String sourceType;
    @Column(name = "source_id") private UUID sourceId;
    @Column(nullable = false, length = 500) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlannedShutdownReadinessSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlannedShutdownItemStatus status = PlannedShutdownItemStatus.PENDING;
    @Column(name = "responsible_employee_id") private UUID responsibleEmployeeId;
    @Column(name = "due_at") private Instant dueAt;
    @Column(columnDefinition = "text") private String evidence;
    @Column(columnDefinition = "text") private String comment;
    @Column(name = "completed_by_id") private UUID completedById;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "order_number", nullable = false) private Integer orderNumber = 0;
}
