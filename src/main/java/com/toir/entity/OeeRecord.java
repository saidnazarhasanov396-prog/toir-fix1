package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oee_records", indexes = {
        @Index(name = "idx_oee_equipment_shift", columnList = "equipment_id,shift_start")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
