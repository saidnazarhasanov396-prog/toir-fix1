package com.toir.dto.warehouseanalytics;

import java.time.LocalDate;
import java.util.UUID;

public record WarehouseReservationRowDto(
        UUID reservationId,
        UUID sparePartId,
        String sparePartName,
        UUID warehouseId,
        String warehouseName,
        UUID workOrderId,
        String workOrderNumber,
        String equipmentName,
        double quantity,
        LocalDate reservedAt,
        String status
) {
}
