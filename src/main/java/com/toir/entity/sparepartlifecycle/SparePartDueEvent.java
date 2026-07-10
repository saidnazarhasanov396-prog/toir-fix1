package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "spare_part_due_events")
@Getter
@Setter
public class SparePartDueEvent extends BaseEntity {

    @Column(name = "installation_id", nullable = false, updatable = false)
    private UUID installationId;

    @Column(name = "applied_rule_id", updatable = false)
    private UUID appliedRuleId;

    @Column(name = "applied_rule_revision", updatable = false)
    private Integer appliedRuleRevision;

    @Column(name = "cycle_key", nullable = false, updatable = false)
    private String cycleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SparePartDueEventState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_action", nullable = false, updatable = false)
    private SparePartDueAction dueAction;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "meter_id")
    private UUID meterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type")
    private MeterType meterType;

    @Column(name = "due_meter_value", precision = 19, scale = 6)
    private BigDecimal dueMeterValue;

    @Column(name = "current_meter_value", precision = 19, scale = 6)
    private BigDecimal currentMeterValue;

    @Column(name = "warning_threshold", precision = 19, scale = 6)
    private BigDecimal warningThreshold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String reasons;

    @Column(name = "first_detected_at", nullable = false, updatable = false)
    private Instant firstDetectedAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "linked_work_order_id")
    private UUID linkedWorkOrderId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "replacement_installation_id")
    private UUID replacementInstallationId;

    @Version
    @Column(nullable = false)
    private long version;
}
