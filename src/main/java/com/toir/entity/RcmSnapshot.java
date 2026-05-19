package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

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
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
