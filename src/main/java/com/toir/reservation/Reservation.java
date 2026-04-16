package com.toir.reservation;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation extends BaseEntity {

    @Column(name = "warehouse_stock_id", nullable = false)
    private UUID warehouseStockId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "reserved_by_id")
    private UUID reservedById;

    @Column(nullable = false)
    private double quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    public UUID getWarehouseStockId() { return warehouseStockId; }
    public void setWarehouseStockId(UUID warehouseStockId) { this.warehouseStockId = warehouseStockId; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public UUID getRepairRequestId() { return repairRequestId; }
    public void setRepairRequestId(UUID repairRequestId) { this.repairRequestId = repairRequestId; }
    public UUID getReservedById() { return reservedById; }
    public void setReservedById(UUID reservedById) { this.reservedById = reservedById; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
}
