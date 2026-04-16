package com.toir.equipmentsparepart;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * Связь оборудование ↔ запчасть. По ТЗ §4.2.8 каждая единица оборудования имеет
 * каталог применимых запчастей со своей нормой расхода и позицией установки.
 */
@Entity
@Table(name = "equipment_spare_parts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipment_id", "spare_part_id", "position"}))
public class EquipmentSparePart extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    /** Позиция установки (например, "подшипник опорный", "уплотнение вала"). */
    @Column(name = "position")
    private String position;

    /** Требуемое количество на одну единицу оборудования. */
    @Column(name = "quantity_per_unit", nullable = false)
    private double quantityPerUnit = 1.0;

    /** Норма расхода в год (штук / ед. оборудования / год). */
    @Column(name = "consumption_rate_per_year")
    private Double consumptionRatePerYear;

    /** Критичность позиции: CRITICAL / STANDARD / OPTIONAL. */
    @Column(name = "criticality")
    private String criticality;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getSparePartId() { return sparePartId; }
    public void setSparePartId(UUID sparePartId) { this.sparePartId = sparePartId; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public double getQuantityPerUnit() { return quantityPerUnit; }
    public void setQuantityPerUnit(double quantityPerUnit) { this.quantityPerUnit = quantityPerUnit; }
    public Double getConsumptionRatePerYear() { return consumptionRatePerYear; }
    public void setConsumptionRatePerYear(Double consumptionRatePerYear) { this.consumptionRatePerYear = consumptionRatePerYear; }
    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
