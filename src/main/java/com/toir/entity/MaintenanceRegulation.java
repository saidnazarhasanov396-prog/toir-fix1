package com.toir.entity;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "maintenance_regulations")
public class MaintenanceRegulation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "equipment_type_id", nullable = false)
    private UUID equipmentTypeId;

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

    public MeterType getTriggerMeterType() { return triggerMeterType; }
    public void setTriggerMeterType(MeterType triggerMeterType) { this.triggerMeterType = triggerMeterType; }
    public Double getTriggerMeterInterval() { return triggerMeterInterval; }
    public void setTriggerMeterInterval(Double triggerMeterInterval) { this.triggerMeterInterval = triggerMeterInterval; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getEquipmentTypeId() { return equipmentTypeId; }
    public void setEquipmentTypeId(UUID equipmentTypeId) { this.equipmentTypeId = equipmentTypeId; }
    public MaintenanceKind getMaintenanceKind() { return maintenanceKind; }
    public void setMaintenanceKind(MaintenanceKind maintenanceKind) { this.maintenanceKind = maintenanceKind; }
    public double getNormativeLaborHours() { return normativeLaborHours; }
    public void setNormativeLaborHours(double normativeLaborHours) { this.normativeLaborHours = normativeLaborHours; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public PeriodicityUnit getPeriodicityUnit() { return periodicityUnit; }
    public void setPeriodicityUnit(PeriodicityUnit periodicityUnit) { this.periodicityUnit = periodicityUnit; }
    public int getPeriodicityValue() { return periodicityValue; }
    public void setPeriodicityValue(int periodicityValue) { this.periodicityValue = periodicityValue; }
    public Integer getToleranceDays() { return toleranceDays; }
    public void setToleranceDays(Integer toleranceDays) { this.toleranceDays = toleranceDays; }
    public boolean isRequiresShutdown() { return requiresShutdown; }
    public void setRequiresShutdown(boolean requiresShutdown) { this.requiresShutdown = requiresShutdown; }
}
