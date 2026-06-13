package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryIssueDto(
        UUID id,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartName,
        BigDecimal quantity,
        String unit,
        UUID takenById,
        String takenByName,
        UUID responsiblePersonId,
        String responsiblePersonName,
        UUID departmentId,
        String departmentName,
        UUID workOrderId,
        String workOrderNumber,
        LocalDate issueDate,
        String documentNumber,
        String comment
) {
}
