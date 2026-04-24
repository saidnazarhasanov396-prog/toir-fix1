package com.toir.entity;

import com.toir.enums.ConditionParameter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Показание оборудования (condition monitoring). По ТЗ §4.2.13 — основа
 * для предиктивной диагностики: вибрация, температура, часы наработки,
 * давление, ток и т.п.
 */
@Entity
@Table(name = "condition_readings",
        indexes = {
                @Index(name = "ix_cond_equipment_param_ts", columnList = "equipment_id,parameter,recorded_at")
        })
public class ConditionReading extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionParameter parameter;

    @Column(nullable = false)
    private double value;

    @Column(nullable = false)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "recorded_by")
    private UUID recordedBy;

    /** Верхняя граница нормы. Если value > warnHigh → WARN. */
    @Column(name = "warn_high")
    private Double warnHigh;

    /** Аварийная граница. Если value > alarmHigh → ALARM. */
    @Column(name = "alarm_high")
    private Double alarmHigh;

    @Column(name = "warn_low")
    private Double warnLow;

    @Column(name = "alarm_low")
    private Double alarmLow;

    /** OK / WARN / ALARM — рассчитано при сохранении. */
    @Column(nullable = false)
    private String severity = "OK";

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public ConditionParameter getParameter() { return parameter; }
    public void setParameter(ConditionParameter parameter) { this.parameter = parameter; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
    public Double getWarnHigh() { return warnHigh; }
    public void setWarnHigh(Double warnHigh) { this.warnHigh = warnHigh; }
    public Double getAlarmHigh() { return alarmHigh; }
    public void setAlarmHigh(Double alarmHigh) { this.alarmHigh = alarmHigh; }
    public Double getWarnLow() { return warnLow; }
    public void setWarnLow(Double warnLow) { this.warnLow = warnLow; }
    public Double getAlarmLow() { return alarmLow; }
    public void setAlarmLow(Double alarmLow) { this.alarmLow = alarmLow; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
