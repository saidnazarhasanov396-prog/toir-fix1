package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "maintenance_due_events")
@Getter
@Setter
public class MaintenanceDueEvent extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "regulation_id")
    private UUID regulationId;

    @Column(name = "equipment_maintenance_rule_id")
    private UUID equipmentMaintenanceRuleId;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceDueEventStatus status = MaintenanceDueEventStatus.DETECTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_status", nullable = false)
    private MaintenanceDueStatus dueStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false)
    private MaintenanceTriggerSource triggerSource;

    @Column(name = "cycle_key", nullable = false)
    private String cycleKey;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type")
    private MeterType meterType;

    @Column(name = "meter_current_value")
    private Double meterCurrentValue;

    @Column(name = "meter_anchor_value")
    private Double meterAnchorValue;

    @Column(name = "meter_interval")
    private Double meterInterval;

    @Column(name = "meter_remaining")
    private Double meterRemaining;

    @Column(name = "created_task_id")
    private UUID createdTaskId;

    @Column(name = "created_work_order_id")
    private UUID createdWorkOrderId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_reason", columnDefinition = "text")
    private String resolutionReason;

    @Column(columnDefinition = "text")
    private String explanation;
}
