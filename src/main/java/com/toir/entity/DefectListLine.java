package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * Строка дефектной ведомости: выявленный дефект, объём работ, материалы, оценка трудозатрат.
 */
@Entity
@Table(name = "defect_list_lines")
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

    public DefectList getDefectList() { return defectList; }
    public void setDefectList(DefectList defectList) { this.defectList = defectList; }
    public UUID getDefectId() { return defectId; }
    public void setDefectId(UUID defectId) { this.defectId = defectId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWorkScope() { return workScope; }
    public void setWorkScope(String workScope) { this.workScope = workScope; }
    public String getMaterialSpecification() { return materialSpecification; }
    public void setMaterialSpecification(String materialSpecification) { this.materialSpecification = materialSpecification; }
    public UUID getSparePartId() { return sparePartId; }
    public void setSparePartId(UUID sparePartId) { this.sparePartId = sparePartId; }
    public double getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(double requiredQuantity) { this.requiredQuantity = requiredQuantity; }
    public double getEstimatedLaborHours() { return estimatedLaborHours; }
    public void setEstimatedLaborHours(double estimatedLaborHours) { this.estimatedLaborHours = estimatedLaborHours; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
}
