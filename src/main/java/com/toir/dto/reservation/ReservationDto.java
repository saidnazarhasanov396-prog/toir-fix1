package com.toir.dto.reservation;

import com.toir.entity.Reservation;
import com.toir.enums.ReservationStatus;
import com.toir.enums.WarehouseStockStatus;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

public record ReservationDto(
        UUID id,
        UUID warehouseStockId,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        UUID workOrderId,
        UUID repairRequestId,
        UUID reservedById,
        UUID requirementId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class)
        BigDecimal quantity,
        ReservationStatus status
) {
    public ReservationDto(
            UUID id,
            UUID warehouseStockId,
            UUID workOrderId,
            UUID repairRequestId,
            UUID reservedById,
            BigDecimal quantity,
            ReservationStatus status
    ) {
        this(id, warehouseStockId, null, null, null, workOrderId, repairRequestId,
                reservedById, null, null, null, null, WarehouseStockStatus.AVAILABLE, quantity, status);
    }

    public static ReservationDto from(Reservation r) {
        return new ReservationDto(r.getId(), r.getWarehouseStockId(), r.getWarehouseId(),
                r.getSparePartId(), r.getBinId(), r.getWorkOrderId(),
                r.getRepairRequestId(), r.getReservedById(), r.getRequirementId(),
                r.getLotNumber(), r.getSerialNumber(), r.getExpiryDate(), r.getStockStatus(),
                r.getQuantity(), r.getStatus());
    }
}
