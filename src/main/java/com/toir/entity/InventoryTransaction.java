package com.toir.entity;

import com.toir.enums.InventoryTransactionType;
import com.toir.enums.InventoryAdjustmentReason;
import com.toir.enums.WarehouseStockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryTransactionType type;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "destination_warehouse_id")
    private UUID destinationWarehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "actual_quantity", precision = 19, scale = 4)
    private BigDecimal actualQuantity;

    @Column(name = "variance", precision = 19, scale = 4)
    private BigDecimal variance;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_reason", length = 30)
    private InventoryAdjustmentReason adjustmentReason;

    @Column(length = 30)
    private String unit;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "taken_by_id")
    private UUID takenById;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "source_bin_id")
    private UUID sourceBinId;

    @Column(name = "destination_bin_id")
    private UUID destinationBinId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (stockStatus == null) {
            stockStatus = WarehouseStockStatus.AVAILABLE;
        }
    }
}
