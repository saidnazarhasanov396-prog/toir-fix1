package com.toir.entity;

import com.toir.enums.ConditionParameter;
import jakarta.persistence.*;
import lombok.*;

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
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}
