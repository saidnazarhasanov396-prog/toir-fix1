package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Замороженное состояние RCM-скоринга по единице оборудования. Создаётся
 * периодически (ежемесячно / при крупных ремонтах) для исторического
 * сравнения и построения трендов.
 */
@Entity
@Table(name = "rcm_snapshots",
        indexes = {
                @Index(name = "ix_rcm_snap_eq_ts", columnList = "equipment_id,captured_at")
        })
public class RcmSnapshot extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "equipment_code", nullable = false)
    private String equipmentCode;

    @Column(name = "equipment_name", nullable = false)
    private String equipmentName;

    @Column(name = "criticality_class")
    private String criticalityClass;

    @Column(nullable = false)
    private int consequence;

    @Column(nullable = false)
    private int probability;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "repair_priority")
    private Integer repairPriority;

    @Column(name = "open_defects", nullable = false)
    private long openDefects;

    @Column(name = "mtbf_hours", nullable = false)
    private double mtbfHours;

    @Column(name = "mttr_hours", nullable = false)
    private double mttrHours;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
    public String getCriticalityClass() { return criticalityClass; }
    public void setCriticalityClass(String criticalityClass) { this.criticalityClass = criticalityClass; }
    public int getConsequence() { return consequence; }
    public void setConsequence(int consequence) { this.consequence = consequence; }
    public int getProbability() { return probability; }
    public void setProbability(int probability) { this.probability = probability; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public Integer getRepairPriority() { return repairPriority; }
    public void setRepairPriority(Integer repairPriority) { this.repairPriority = repairPriority; }
    public long getOpenDefects() { return openDefects; }
    public void setOpenDefects(long openDefects) { this.openDefects = openDefects; }
    public double getMtbfHours() { return mtbfHours; }
    public void setMtbfHours(double mtbfHours) { this.mtbfHours = mtbfHours; }
    public double getMttrHours() { return mttrHours; }
    public void setMttrHours(double mttrHours) { this.mttrHours = mttrHours; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
}
