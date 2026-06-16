package com.toir.dto.sparepart;

import com.toir.enums.StockMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SparePartRecentMovementDto(
        UUID id,
        StockMovementType type,
        double quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        LocalDate movementDate,
        String warehouseName,
        String documentNumber,
        UUID workOrderId,
        String workOrderNumber,
        String workOrderName,
        UUID departmentId,
        String departmentName
) {
}
