package com.toir.entity.defects;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Строка дефектной ведомости: выявленный дефект, объём работ, материалы, оценка трудозатрат.
 */
@Entity
@Table(name = "defect_list_lines")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DefectListLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "defect_list_id", nullable = false)
    private DefectList defectList;

    @Column(name = "defect_id")
    private UUID defectId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "work_scope", columnDefinition = "text")
    private String workScope;

    @Column(name = "material_specification", columnDefinition = "text")
    private String materialSpecification;

    @Column(name = "spare_part_id")
    private UUID sparePartId;

    @Column(name = "required_quantity", nullable = false)
    private double requiredQuantity;

    @Column(name = "estimated_labor_hours", nullable = false)
    private double estimatedLaborHours;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost;

}
