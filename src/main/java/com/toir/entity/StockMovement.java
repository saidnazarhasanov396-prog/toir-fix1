package com.toir.entity;
import com.toir.entity.StockMovementType;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public UUID getSparePartId() { return sparePartId; }
    public void setSparePartId(UUID sparePartId) { this.sparePartId = sparePartId; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public StockMovementType getType() { return type; }
    public void setType(StockMovementType type) { this.type = type; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public Double getUnitCost() { return unitCost; }
    public void setUnitCost(Double unitCost) { this.unitCost = unitCost; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
