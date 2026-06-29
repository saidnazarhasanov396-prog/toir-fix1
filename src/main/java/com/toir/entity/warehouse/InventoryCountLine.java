package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.InventoryCountLineStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_lines")
@Getter
@Setter
public class InventoryCountLine extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(name = "expected_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedQty = BigDecimal.ZERO;

    @Column(name = "counted_qty", precision = 19, scale = 4)
    private BigDecimal countedQty;

    @Column(name = "variance_qty", precision = 19, scale = 4)
    private BigDecimal varianceQty;

    @Column(length = 64)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryCountLineStatus status = InventoryCountLineStatus.OPEN;

    @Column(name = "counted_by_id")
    private UUID countedById;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(name = "variance_reason", columnDefinition = "text")
    private String varianceReason;

    @PrePersist
    void prepareForInsert() {
        if (stockStatus == null) {
            stockStatus = WarehouseStockStatus.AVAILABLE;
        }
        if (expectedQty == null) {
            expectedQty = BigDecimal.ZERO;
        }
        if (status == null) {
            status = InventoryCountLineStatus.OPEN;
        }
    }
}
