package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
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
@Table(name = "warehouse_writeoff_requests")
@Getter
@Setter
public class WarehouseWriteoffRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 64)
    private String requestNumber;

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
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.WRITEOFF_PENDING;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WarehouseWriteoffStatus status = WarehouseWriteoffStatus.DRAFT;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "stock_movement_id")
    private UUID stockMovementId;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(columnDefinition = "text")
    private String comment;

    @PrePersist
    void prepareForInsert() {
        if (stockStatus == null) {
            stockStatus = WarehouseStockStatus.WRITEOFF_PENDING;
        }
        if (status == null) {
            status = WarehouseWriteoffStatus.DRAFT;
        }
    }
}
