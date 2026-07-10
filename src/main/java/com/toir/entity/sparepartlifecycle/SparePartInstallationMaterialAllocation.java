package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spare_part_installation_material_allocations")
@Getter
@Setter
public class SparePartInstallationMaterialAllocation extends BaseEntity {

    @Column(name = "installation_id", nullable = false, updatable = false)
    private UUID installationId;

    @Column(name = "repair_material_usage_id", nullable = false, unique = true, updatable = false)
    private UUID repairMaterialUsageId;

    @Column(name = "allocated_quantity", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal allocatedQuantity;

    @Column(name = "serial_number_snapshot", updatable = false)
    private String serialNumberSnapshot;

    @Column(name = "lot_number_snapshot", updatable = false)
    private String lotNumberSnapshot;
}
