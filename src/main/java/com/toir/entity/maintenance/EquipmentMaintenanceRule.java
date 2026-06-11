package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipment_maintenance_rules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentMaintenanceRule extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "base_regulation_id")
    private UUID baseRegulationId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

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

    @Column(name = "disables_base_regulation", nullable = false)
    private boolean disablesBaseRegulation;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_schedule_policy")
    private MaintenanceInitialSchedulePolicy initialSchedulePolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_action")
    private AutomationAction automationAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_result_action")
    private ApprovalResultAction approvalResultAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_policy")
    private DuplicatePolicy duplicatePolicy;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "lead_meter_percent")
    private Double leadMeterPercent;

    @Column(name = "default_department_id")
    private UUID defaultDepartmentId;

    @Column(name = "default_responsible_id")
    private UUID defaultResponsibleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority")
    private PriorityLevel defaultPriority;

    @Column(name = "requires_approval")
    private Boolean requiresApproval;

    @Column(name = "approval_role")
    private String approvalRole;

    @Column(name = "approval_permission")
    private String approvalPermission;
}
