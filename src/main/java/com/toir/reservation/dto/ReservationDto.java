package com.toir.reservation.dto;

import com.toir.reservation.Reservation;
import com.toir.reservation.ReservationStatus;

import java.util.UUID;

public record ReservationDto(
        UUID id,
        UUID warehouseStockId,
        UUID workOrderId,
        UUID repairRequestId,
        UUID reservedById,
        double quantity,
        ReservationStatus status
) {
    public static ReservationDto from(Reservation r) {
        return new ReservationDto(r.getId(), r.getWarehouseStockId(), r.getWorkOrderId(),
                r.getRepairRequestId(), r.getReservedById(), r.getQuantity(), r.getStatus());
    }
}
