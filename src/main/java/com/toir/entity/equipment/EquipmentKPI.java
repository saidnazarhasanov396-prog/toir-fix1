package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_kpis")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentKPI extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "operating_hours")
    private Double operatingHours;

    @Column(name = "downtime_hours")
    private Double downtimeHours;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Column(name = "repair_count")
    private Integer repairCount;

    @Column(name = "mtbf_hours")
    private Double mtbfHours;

    @Column(name = "mttr_hours")
    private Double mttrHours;

    private Double availability;

}
