package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.StockLedgerMovementType;
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
@Table(name = "warehouse_stock_ledgers")
@Getter
@Setter
public class WarehouseStockLedger extends BaseEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 64)
    private StockLedgerMovementType movementType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 19, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "reference_type", length = 100)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_doc_no", length = 100)
    private String referenceDocNo;

    @Column(name = "idempotency_key", length = 512)
    private String idempotencyKey;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @PrePersist
    void prepareForInsert() {
        if (postedAt == null) {
            postedAt = Instant.now();
        }
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        if (stockStatus == null) {
            stockStatus = WarehouseStockStatus.AVAILABLE;
        }
        if (unitCost != null && totalCost == null) {
            totalCost = quantity.multiply(unitCost);
        }
    }
}
