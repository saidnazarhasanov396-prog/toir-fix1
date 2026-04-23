package com.toir.entity;
import com.toir.entity.Equipment;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Поверка средства измерения. По ТЗ §4.2.16 — учёт периодических поверок
 * приборов КИПиА (термопары, манометры, расходомеры и т.п.) с фиксацией
 * даты следующей поверки.
 */
@Entity
@Table(name = "calibration_records",
        indexes = {
                @Index(name = "ix_calib_eq_next", columnList = "equipment_id,next_due_at")
        })
public class CalibrationRecord extends BaseEntity {

    /** Поверяемый прибор (Equipment типа INSTRUMENT). */
    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "certificate_number")
    private String certificateNumber;

    /** Кто проводил поверку (организация / лаборатория). */
    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDate performedAt;

    @Column(name = "next_due_at")
    private LocalDate nextDueAt;

    /** PASS / FAIL / CONDITIONAL. */
    @Column(nullable = false)
    private String result = "PASS";

    /** Допустимая погрешность (±, в единицах прибора). */
    @Column(name = "tolerance")
    private Double tolerance;

    @Column(name = "measured_error")
    private Double measuredError;

    @Column(name = "unit")
    private String unit;

    @Column(name = "document_file_id")
    private UUID documentFileId;

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public LocalDate getPerformedAt() { return performedAt; }
    public void setPerformedAt(LocalDate performedAt) { this.performedAt = performedAt; }
    public LocalDate getNextDueAt() { return nextDueAt; }
    public void setNextDueAt(LocalDate nextDueAt) { this.nextDueAt = nextDueAt; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Double getTolerance() { return tolerance; }
    public void setTolerance(Double tolerance) { this.tolerance = tolerance; }
    public Double getMeasuredError() { return measuredError; }
    public void setMeasuredError(Double measuredError) { this.measuredError = measuredError; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public UUID getDocumentFileId() { return documentFileId; }
    public void setDocumentFileId(UUID documentFileId) { this.documentFileId = documentFileId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
