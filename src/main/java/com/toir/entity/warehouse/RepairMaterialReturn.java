package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.WarehouseStockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "repair_material_returns")
@Getter
@Setter
public class RepairMaterialReturn extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "material_usage_id")
    private UUID materialUsageId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

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

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "returned_by_id")
    private UUID returnedById;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @Column(name = "stock_movement_id")
    private UUID stockMovementId;

    @Column(name = "inventory_transaction_id")
    private UUID inventoryTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepairMaterialReturnStatus status = RepairMaterialReturnStatus.POSTED;

    @PrePersist
    void prepareForInsert() {
        if (stockStatus == null) {
            stockStatus = WarehouseStockStatus.AVAILABLE;
        }
        if (status == null) {
            status = RepairMaterialReturnStatus.POSTED;
        }
    }
}
