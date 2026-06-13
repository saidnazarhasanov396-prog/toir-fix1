package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.MaintenanceRecalculationPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "maintenance_completion_anchors")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceCompletionAnchor extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "regulation_id")
    private UUID regulationId;

    @Column(name = "equipment_maintenance_rule_id")
    private UUID equipmentMaintenanceRuleId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "ppr_task_id")
    private UUID pprTaskId;

    @Column(name = "maintenance_due_event_id")
    private UUID maintenanceDueEventId;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "planned_due_at")
    private Instant plannedDueAt;

    @Column(name = "planned_meter_value", precision = 19, scale = 4)
    private BigDecimal plannedMeterValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "recalculation_policy", nullable = false)
    private MaintenanceRecalculationPolicy recalculationPolicy =
            MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meter_snapshots", nullable = false, columnDefinition = "jsonb")
    private String meterSnapshots = "[]";

    @Column(nullable = false)
    private String source;

    @Column(columnDefinition = "text")
    private String note;
}
