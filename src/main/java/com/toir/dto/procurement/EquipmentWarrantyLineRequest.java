package com.toir.dto.procurement;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentWarrantyLineRequest(
        UUID procurementLineId,
        Boolean hasWarranty,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        Integer warrantyDurationMonths,
        UUID warrantySupplierId
) {
}
