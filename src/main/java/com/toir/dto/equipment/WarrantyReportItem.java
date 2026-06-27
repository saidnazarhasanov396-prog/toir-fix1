package com.toir.dto.equipment;

import java.time.LocalDate;
import java.util.UUID;

public record WarrantyReportItem(
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        String departmentName,
        boolean hasWarranty,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        String warrantyStatus,
        long daysUntilExpiry,
        UUID warrantySupplierId,
        String warrantySupplierName
) {}
