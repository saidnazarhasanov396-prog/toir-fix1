package com.toir.dto.warehouseanalytics;

import java.time.LocalDate;
import java.util.UUID;
import java.math.BigDecimal;

public record WarehouseReservationRowDto(
        UUID reservationId,
        UUID sparePartId,
        String sparePartName,
        UUID warehouseId,
        String warehouseName,
        UUID workOrderId,
        String workOrderNumber,
        String equipmentName,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal quantity,
        LocalDate reservedAt,
        String status
) {
}
