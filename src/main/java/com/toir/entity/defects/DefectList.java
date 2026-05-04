package com.toir.entity.defects;

import com.toir.entity.BaseEntity;
import com.toir.enums.DefectListStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Дефектная ведомость — коллекция выявленных дефектов с объёмами работ,
 * материалами и трудозатратами. Основание для создания наряда.
 */
@Entity
@Table(name = "defect_lists")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DefectList extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectListStatus status = DefectListStatus.DRAFT;

    @Column(name = "total_labor_hours", nullable = false)
    private double totalLaborHours;

    @Column(name = "total_estimated_cost", nullable = false)
    private double totalEstimatedCost;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "defectList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DefectListLine> lines = new ArrayList<>();

}
