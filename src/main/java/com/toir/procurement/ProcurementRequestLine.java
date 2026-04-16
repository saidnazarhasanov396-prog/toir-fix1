package com.toir.procurement;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "procurement_request_lines")
public class ProcurementRequestLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ProcurementRequest request;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "quantity", nullable = false)
    private double quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost = 0.0;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public ProcurementRequest getRequest() { return request; }
    public void setRequest(ProcurementRequest request) { this.request = request; }
    public UUID getSparePartId() { return sparePartId; }
    public void setSparePartId(UUID sparePartId) { this.sparePartId = sparePartId; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
