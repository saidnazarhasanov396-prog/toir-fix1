package com.toir.dto.reservation;

import com.toir.entity.Reservation;
import com.toir.enums.ReservationStatus;

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
