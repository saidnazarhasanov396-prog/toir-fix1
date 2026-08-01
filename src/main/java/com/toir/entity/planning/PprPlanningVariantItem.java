package com.toir.entity.planning;

import com.toir.entity.BaseEntity;
import com.toir.enums.CompletionEvidenceType;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PriorityLevel;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(
        name = "ppr_planning_variant_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ppr_variant_item_revision_source",
                columnNames = {"variant_id", "revision", "source_item_key"})
)
@Immutable
@AttributeOverrides({
        @AttributeOverride(
                name = "createdAt",
                column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(
                name = "updatedAt",
                column = @Column(name = "updated_at", nullable = false, updatable = false)),
        @AttributeOverride(
                name = "isDeleted",
                column = @Column(name = "is_deleted", nullable = false, updatable = false))
})
@Getter
@Setter
public class PprPlanningVariantItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, updatable = false)
    private PprPlanningVariant variant;

    @Column(nullable = false, updatable = false)
    private long revision;

    @Column(name = "source_item_key", nullable = false, length = 64, updatable = false)
    private String sourceItemKey;

    @Column(name = "source_item_key_version", nullable = false, updatable = false)
    private int sourceItemKeyVersion;

    @Column(name = "equipment_id", nullable = false, updatable = false)
    private UUID equipmentId;

    @Column(name = "regulation_id", updatable = false)
    private UUID regulationId;

    @Column(name = "maintenance_rule_id", updatable = false)
    private UUID maintenanceRuleId;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", length = 64, updatable = false)
    private MaintenanceKind maintenanceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 64, updatable = false)
    private MaintenanceTriggerPolicy triggerType;

    @Column(name = "trigger_discriminator", length = 255, updatable = false)
    private String triggerDiscriminator;

    @Column(name = "cycle_ordinal", updatable = false)
    private Long cycleOrdinal;

    @Column(name = "planned_date", nullable = false, updatable = false)
    private LocalDate plannedDate;

    @Column(name = "scheduled_start", nullable = false, updatable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false, updatable = false)
    private LocalDateTime scheduledEnd;

    @Column(name = "due_date", updatable = false)
    private LocalDateTime dueDate;

    @Column(name = "normative_labor_hours", precision = 19, scale = 4, updatable = false)
    private BigDecimal normativeLaborHours;

    @Enumerated(EnumType.STRING)
    @Column(length = 64, updatable = false)
    private PriorityLevel priority;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "equipment_code_snapshot", nullable = false, length = 255, updatable = false)
    private String equipmentCodeSnapshot;

    @Column(name = "equipment_name_snapshot", nullable = false, length = 255, updatable = false)
    private String equipmentNameSnapshot;

    @Column(name = "regulation_name_snapshot", length = 255, updatable = false)
    private String regulationNameSnapshot;

    @Column(name = "maintenance_rule_name_snapshot", length = 255, updatable = false)
    private String maintenanceRuleNameSnapshot;

    @Column(name = "template_name_snapshot", length = 255, updatable = false)
    private String templateNameSnapshot;

    @Column(name = "task_title_snapshot", nullable = false, length = 500, updatable = false)
    private String taskTitleSnapshot;

    @Column(name = "work_order_lead_days", nullable = false, updatable = false)
    private int workOrderLeadDays;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ppr_planning_variant_item_required_evidence",
            joinColumns = @JoinColumn(name = "variant_item_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 32)
    private Set<CompletionEvidenceType> requiredEvidenceTypes = new HashSet<>();
}
