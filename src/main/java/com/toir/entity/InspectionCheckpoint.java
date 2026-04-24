package com.toir.entity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inspection_checkpoints")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InspectionCheckpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private InspectionRoute route;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String instruction;

    /** Тип проверки: VISUAL / MEASUREMENT / BOOLEAN / READING. */
    @Column(nullable = false)
    private String checkType = "VISUAL";

    /** Expected parameter, если checkType = READING/MEASUREMENT. */
    @Column(name = "expected_min")
    private Double expectedMin;

    @Column(name = "expected_max")
    private Double expectedMax;

    @Column(name = "expected_unit")
    private String expectedUnit;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

}
