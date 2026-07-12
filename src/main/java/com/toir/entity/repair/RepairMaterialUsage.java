package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import com.toir.enums.WarehouseStockStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "repair_material_usages")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairMaterialUsage extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "stock_movement_id")
    private UUID stockMovementId;

    /**
     * Rejalashtirilgan ehtiyot qism (WorkOrderSparePartRequirement) ga havola.
     * Agar null bo'lsa — requirement ga bog'liq bo'lmagan qo'shimcha material.
     */
    @Column(name = "requirement_id")
    private UUID requirementId;

    /**
     * Agar rejalashtirilgan ehtiyot qism o'rniga boshqasi ishlatilgan bo'lsa —
     * asl rejalashtirilgan spare part id shu yerda saqlanadi.
     * Masalan: 5x30-111 rejalashtirilgan edi, 5x30-222 ishlatildi →
     *   sparePartId = 5x30-222, replacedSparePartId = 5x30-111
     */
    @Column(name = "replaced_spare_part_id")
    private UUID replacedSparePartId;

    @Column(name = "issued_by_id")
    private UUID issuedById;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(nullable = false, precision = 19, scale = 4)
    private java.math.BigDecimal quantity;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(columnDefinition = "text")
    private String notes;
}
