package com.toir.entity;
import com.toir.enums.ReservationStatus;
import com.toir.enums.WarehouseStockStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Reservation extends BaseEntity {

    @Column(name = "warehouse_stock_id")
    private UUID warehouseStockId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "spare_part_id")
    private UUID sparePartId;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "requirement_id")
    private UUID requirementId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "reserved_by_id")
    private UUID reservedById;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(nullable = false)
    private double quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

}
