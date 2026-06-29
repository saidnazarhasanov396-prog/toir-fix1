package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "warehouse_task_lines")
@Getter
@Setter
public class WarehouseTaskLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private WarehouseTask task;

    @Column(name = "spare_part_id")
    private UUID sparePartId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "from_bin_id")
    private UUID fromBinId;

    @Column(name = "to_bin_id")
    private UUID toBinId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(name = "planned_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedQty = BigDecimal.ZERO;

    @Column(name = "actual_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal actualQty = BigDecimal.ZERO;

    @Column(length = 64)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WarehouseTaskLineStatus status = WarehouseTaskLineStatus.OPEN;

    @Column(name = "scan_confirmed", nullable = false)
    private boolean scanConfirmed;

    @Column(name = "exception_reason", columnDefinition = "text")
    private String exceptionReason;
}
