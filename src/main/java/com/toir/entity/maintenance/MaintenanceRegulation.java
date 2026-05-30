package com.toir.entity.maintenance;
import com.toir.entity.BaseEntity;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;

import jakarta.persistence.*;
import lombok.*;

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
}
