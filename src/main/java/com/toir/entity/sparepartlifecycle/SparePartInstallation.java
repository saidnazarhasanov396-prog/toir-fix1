package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartRemovalDisposition;
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
@Table(name = "spare_part_installations")
@Getter
@Setter
public class SparePartInstallation extends BaseEntity {

    @Column(name = "equipment_id", nullable = false, updatable = false)
    private UUID equipmentId;

    @Column(name = "equipment_node_id", updatable = false)
    private UUID equipmentNodeId;

    @Column(name = "normalized_slot_code", nullable = false, updatable = false, length = 128)
    private String normalizedSlotCode;

    @Column(name = "position_key", nullable = false, updatable = false, length = 512)
    private String positionKey;

    @Column(name = "position_label_snapshot")
    private String positionLabelSnapshot;

    @Column(name = "spare_part_id", nullable = false, updatable = false)
    private UUID sparePartId;

    @Column(nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal quantity;

    @Column(name = "serial_number_snapshot", updatable = false)
    private String serialNumberSnapshot;

    @Column(name = "lot_number_snapshot", updatable = false)
    private String lotNumberSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SparePartInstallationStatus status = SparePartInstallationStatus.ACTIVE;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "install_work_order_id", updatable = false)
    private UUID installWorkOrderId;

    @Column(name = "remove_work_order_id")
    private UUID removeWorkOrderId;

    @Column(name = "source_material_usage_id", updatable = false)
    private UUID sourceMaterialUsageId;

    @Column(name = "installed_by", nullable = false, updatable = false)
    private UUID installedBy;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_disposition")
    private SparePartRemovalDisposition removalDisposition;

    @Column(name = "removal_reason", columnDefinition = "text")
    private String removalReason;

    @Column(name = "replaces_installation_id", updatable = false)
    private UUID replacesInstallationId;

    @Column(name = "replaced_by_installation_id")
    private UUID replacedByInstallationId;

    @Column(name = "replacement_correlation_id", updatable = false)
    private UUID replacementCorrelationId;

    @Column(name = "applied_life_rule_id", updatable = false)
    private UUID appliedLifeRuleId;

    @Column(name = "applied_rule_revision", updatable = false)
    private Integer appliedRuleRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied_rule_snapshot", columnDefinition = "jsonb", updatable = false)
    private String appliedRuleSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_evaluation_state", nullable = false)
    private SparePartLifecycleEvaluationState lifecycleEvaluationState = SparePartLifecycleEvaluationState.OK;

    @Column(name = "next_calendar_due_at")
    private Instant nextCalendarDueAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_details", columnDefinition = "jsonb")
    private String evaluationDetails;

    @Version
    @Column(nullable = false)
    private long version;
}
