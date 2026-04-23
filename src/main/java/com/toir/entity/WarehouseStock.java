package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "warehouse_stocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "spare_part_id"}))
public class WarehouseStock extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "reserved_qty", nullable = false)
    private double reservedQty;

    @Column(name = "min_qty", nullable = false)
    private double minQty;

    @Column(name = "max_qty")
    private Double maxQty;

    @Column(name = "reorder_point")
    private Double reorderPoint;

    @Column(name = "reorder_qty")
    private Double reorderQty;

    @Column(name = "avg_daily_usage")
    private Double avgDailyUsage;

    @Column(name = "bin_location")
    private String binLocation;

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public UUID getSparePartId() { return sparePartId; }
    public void setSparePartId(UUID sparePartId) { this.sparePartId = sparePartId; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public double getReservedQty() { return reservedQty; }
    public void setReservedQty(double reservedQty) { this.reservedQty = reservedQty; }
    public double getMinQty() { return minQty; }
    public void setMinQty(double minQty) { this.minQty = minQty; }
    public Double getMaxQty() { return maxQty; }
    public void setMaxQty(Double maxQty) { this.maxQty = maxQty; }
    public Double getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(Double reorderPoint) { this.reorderPoint = reorderPoint; }
    public Double getReorderQty() { return reorderQty; }
    public void setReorderQty(Double reorderQty) { this.reorderQty = reorderQty; }
    public Double getAvgDailyUsage() { return avgDailyUsage; }
    public void setAvgDailyUsage(Double avgDailyUsage) { this.avgDailyUsage = avgDailyUsage; }
    public String getBinLocation() { return binLocation; }
    public void setBinLocation(String binLocation) { this.binLocation = binLocation; }

    public double getAvailable() { return quantity - reservedQty; }
}
