package com.toir.entity.maintenance;
import com.toir.entity.BaseEntity;
import com.toir.enums.TaskExecutionStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_order_tasks")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkOrderTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskExecutionStatus status = TaskExecutionStatus.TODO;

    @Column(name = "assigned_to_id")
    private UUID assignedToId;

    @Column(name = "planned_hours")
    private Double plannedHours;

    @Column(name = "actual_hours")
    private Double actualHours;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @Column(name = "source_operation_id")
    private UUID sourceOperationId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
