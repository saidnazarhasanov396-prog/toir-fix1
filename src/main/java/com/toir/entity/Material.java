package com.toir.entity;

import com.toir.entity.BaseEntity;
import com.toir.entity.InventoryItemKind;
import jakarta.persistence.*;

@Entity
@Table(name = "materials")
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public InventoryItemKind getKind() { return kind; }
    public void setKind(InventoryItemKind kind) { this.kind = kind; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public double getMinStock() { return minStock; }
    public void setMinStock(double minStock) { this.minStock = minStock; }
}
