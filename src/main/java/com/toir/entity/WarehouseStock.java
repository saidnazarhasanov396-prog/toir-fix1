package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "warehouse_stocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "spare_part_id"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
