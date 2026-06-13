package com.toir.dto.inventory;

import com.toir.enums.InventoryMovementClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryMovementAnalyticsDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        BigDecimal issuedQuantity,
        BigDecimal issueAmount,
        BigDecimal averageMonthlyConsumption,
        LocalDate lastMovementDate,
        InventoryMovementClass movementClass
) {
}
