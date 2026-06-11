package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(columnDefinition = "text")
    private String notes;
}
