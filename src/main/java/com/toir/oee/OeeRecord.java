package com.toir.oee;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oee_records", indexes = {
        @Index(name = "idx_oee_equipment_shift", columnList = "equipment_id,shift_start")
})
public class OeeRecord extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "shift_start", nullable = false)
    private Instant shiftStart;

    @Column(name = "shift_end", nullable = false)
    private Instant shiftEnd;

    @Column(name = "planned_production_minutes", nullable = false)
    private double plannedProductionMinutes;

    @Column(name = "run_minutes", nullable = false)
    private double runMinutes;

    @Column(name = "ideal_cycle_seconds", nullable = false)
    private double idealCycleSeconds;

    @Column(name = "total_count", nullable = false)
    private double totalCount;

    @Column(name = "good_count", nullable = false)
    private double goodCount;

    @Column(name = "availability", nullable = false)
    private double availability;

    @Column(name = "performance", nullable = false)
    private double performance;

    @Column(name = "quality", nullable = false)
    private double quality;

    @Column(name = "oee", nullable = false)
    private double oee;

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public Instant getShiftStart() { return shiftStart; }
    public void setShiftStart(Instant shiftStart) { this.shiftStart = shiftStart; }
    public Instant getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(Instant shiftEnd) { this.shiftEnd = shiftEnd; }
    public double getPlannedProductionMinutes() { return plannedProductionMinutes; }
    public void setPlannedProductionMinutes(double v) { this.plannedProductionMinutes = v; }
    public double getRunMinutes() { return runMinutes; }
    public void setRunMinutes(double runMinutes) { this.runMinutes = runMinutes; }
    public double getIdealCycleSeconds() { return idealCycleSeconds; }
    public void setIdealCycleSeconds(double v) { this.idealCycleSeconds = v; }
    public double getTotalCount() { return totalCount; }
    public void setTotalCount(double totalCount) { this.totalCount = totalCount; }
    public double getGoodCount() { return goodCount; }
    public void setGoodCount(double goodCount) { this.goodCount = goodCount; }
    public double getAvailability() { return availability; }
    public void setAvailability(double availability) { this.availability = availability; }
    public double getPerformance() { return performance; }
    public void setPerformance(double performance) { this.performance = performance; }
    public double getQuality() { return quality; }
    public void setQuality(double quality) { this.quality = quality; }
    public double getOee() { return oee; }
    public void setOee(double oee) { this.oee = oee; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
