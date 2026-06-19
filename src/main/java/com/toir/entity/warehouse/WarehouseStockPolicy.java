package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "warehouse_stock_policies")
@Getter
@Setter
public class WarehouseStockPolicy extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

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
}
