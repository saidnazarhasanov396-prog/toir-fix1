package com.toir.entity;
import com.toir.entity.MeterType;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_meters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipment_id", "meter_type", "name"}))
public class EquipmentMeter extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type", nullable = false)
    private MeterType meterType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    @Column(name = "current_value", nullable = false)
    private double currentValue;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "rollover_value")
    private Double rolloverValue;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public MeterType getMeterType() { return meterType; }
    public void setMeterType(MeterType meterType) { this.meterType = meterType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
    public Double getRolloverValue() { return rolloverValue; }
    public void setRolloverValue(Double rolloverValue) { this.rolloverValue = rolloverValue; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
