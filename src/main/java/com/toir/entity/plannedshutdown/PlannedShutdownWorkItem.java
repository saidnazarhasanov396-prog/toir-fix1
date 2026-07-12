package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PriorityLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_work_items")
@Getter
@Setter
public class PlannedShutdownWorkItem extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false)
    private UUID plannedShutdownId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private PlannedShutdownWorkItemSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Column(name = "requires_shutdown", nullable = false)
    private boolean requiresShutdown = true;

    @Column(name = "requires_isolation", nullable = false)
    private boolean requiresIsolation;

    @Column(name = "planned_duration_minutes")
    private Integer plannedDurationMinutes;

    @Column(length = 32)
    private String criticality;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlannedShutdownItemStatus status = PlannedShutdownItemStatus.PENDING;
}
