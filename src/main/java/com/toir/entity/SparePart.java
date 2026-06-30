package com.toir.entity;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.CriticalityLevel;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "spare_parts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SparePart extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryItemKind kind = InventoryItemKind.SPARE_PART;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private SparePartType type;

    @Column(name = "type", nullable = false)
    private String legacyType = "OTHER";

    @Column(name = "mxik_id")
    private UUID mxikId;

    @Column(name = "unit", nullable = false)
    private String unit;

    private String specification;
    private String manufacturer;

    @Column(name = "min_stock", nullable = false)
    private double minStock;

    @Column(name = "preferred_supplier_id")
    private UUID preferredSupplierId;

    @Column(name = "preferred_counteragent_id")
    private UUID preferredCounteragentId;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "last_purchase_price", precision = 19, scale = 2)
    private BigDecimal lastPurchasePrice;

    @Column(name = "average_cost", precision = 19, scale = 2)
    private BigDecimal averageCost;

    @Column(name = "last_purchase_cost", precision = 19, scale = 2)
    private BigDecimal lastPurchaseCost;

    @Column(name = "inventory_value", precision = 19, scale = 2)
    private BigDecimal inventoryValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "criticality", length = 20)
    private CriticalityLevel criticality = CriticalityLevel.LOW;
}
