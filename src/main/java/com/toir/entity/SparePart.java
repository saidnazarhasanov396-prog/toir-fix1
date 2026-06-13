package com.toir.entity;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.SparePartType;

import jakarta.persistence.*;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SparePartType type = SparePartType.OTHER;

    @Column(name = "unit", nullable = false)
    private String unit;

    private String specification;
    private String manufacturer;

    @Column(name = "min_stock", nullable = false)
    private double minStock;
}
