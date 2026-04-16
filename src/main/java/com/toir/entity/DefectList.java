package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Дефектная ведомость — коллекция выявленных дефектов с объёмами работ,
 * материалами и трудозатратами. Основание для создания наряда.
 */
@Entity
@Table(name = "defect_lists")
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getRepairRequestId() { return repairRequestId; }
    public void setRepairRequestId(UUID repairRequestId) { this.repairRequestId = repairRequestId; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public UUID getApprovedById() { return approvedById; }
    public void setApprovedById(UUID approvedById) { this.approvedById = approvedById; }
    public DefectListStatus getStatus() { return status; }
    public void setStatus(DefectListStatus status) { this.status = status; }
    public double getTotalLaborHours() { return totalLaborHours; }
    public void setTotalLaborHours(double totalLaborHours) { this.totalLaborHours = totalLaborHours; }
    public double getTotalEstimatedCost() { return totalEstimatedCost; }
    public void setTotalEstimatedCost(double totalEstimatedCost) { this.totalEstimatedCost = totalEstimatedCost; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<DefectListLine> getLines() { return lines; }
    public void setLines(List<DefectListLine> lines) { this.lines = lines; }
}
