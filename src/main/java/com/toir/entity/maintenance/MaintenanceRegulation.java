package com.toir.entity.maintenance;
import com.toir.entity.BaseEntity;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.CompletionEvidenceType;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "maintenance_regulations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceRegulation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "equipment_type_id", nullable = false)
    private UUID equipmentTypeId;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_kind", nullable = false)
    private MaintenanceKind maintenanceKind;

    @Column(name = "normative_labor_hours", nullable = false)
    private double normativeLaborHours;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity_unit", nullable = false)
    private PeriodicityUnit periodicityUnit;

    @Column(name = "periodicity_value", nullable = false)
    private int periodicityValue;

    @Column(name = "tolerance_days")
    private Integer toleranceDays;

    @Column(name = "requires_shutdown", nullable = false)
    private boolean requiresShutdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_meter_type")
    private MeterType triggerMeterType;

    @Column(name = "trigger_meter_interval")
    private Double triggerMeterInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_policy", nullable = false)
    private MaintenanceTriggerPolicy triggerPolicy = MaintenanceTriggerPolicy.ANY;

    @Enumerated(EnumType.STRING)
    @Column(name = "recalculation_policy", nullable = false)
    private MaintenanceRecalculationPolicy recalculationPolicy =
            MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_schedule_policy", nullable = false)
    private MaintenanceInitialSchedulePolicy initialSchedulePolicy =
            MaintenanceInitialSchedulePolicy.FROM_OPERATION_START;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_action", nullable = false)
    private AutomationAction automationAction = AutomationAction.REQUIRE_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_result_action", nullable = false)
    private ApprovalResultAction approvalResultAction = ApprovalResultAction.CREATE_TASK;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_policy", nullable = false)
    private DuplicatePolicy duplicatePolicy = DuplicatePolicy.ONE_ITEM_PER_CYCLE;

    @Builder.Default
    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays = 7;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "maintenance_regulation_required_evidence",
            joinColumns = @JoinColumn(name = "regulation_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 32)
    private Set<CompletionEvidenceType> requiredEvidenceTypes = new HashSet<>();

    @Column(name = "lead_meter_percent")
    private Double leadMeterPercent;

    @Column(name = "default_department_id")
    private UUID defaultDepartmentId;

    @Column(name = "default_responsible_id")
    private UUID defaultResponsibleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority")
    private PriorityLevel defaultPriority;

    @Column(name = "requires_approval", nullable = false)
    @Deprecated
    private boolean requiresApproval = true;

    @Column(name = "approval_role")
    private String approvalRole;

    @Column(name = "approval_permission")
    private String approvalPermission;
}
