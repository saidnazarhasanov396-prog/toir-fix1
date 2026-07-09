package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.WarehouseStockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Table(name = "warehouse_writeoff_allocations")
@Getter
@Setter
public class WarehouseWriteoffAllocation extends BaseEntity {

    @Column(name = "writeoff_request_id", nullable = false)
    private UUID writeoffRequestId;

    @Column(name = "source_balance_id")
    private UUID sourceBalanceId;

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
    @Column(name = "source_stock_status", nullable = false, length = 32)
    private WarehouseStockStatus sourceStockStatus;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;
}
