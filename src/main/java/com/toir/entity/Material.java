package com.toir.entity;

import com.toir.enums.InventoryItemKind;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "materials")@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Material extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryItemKind kind = InventoryItemKind.MATERIAL;

    @Column(nullable = false)
    private String unit;

    private String specification;

    @Column(name = "min_stock", nullable = false)
    private double minStock;
}
