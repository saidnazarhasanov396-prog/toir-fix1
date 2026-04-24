package com.toir.entity;
import jakarta.persistence.*;
import lombok.*;

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
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
