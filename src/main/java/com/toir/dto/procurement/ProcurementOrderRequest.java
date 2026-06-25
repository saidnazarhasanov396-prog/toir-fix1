package com.toir.dto.procurement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementOrderRequest(
        UUID supplierId,
        LocalDate expectedDeliveryDate,
        String comment,
        List<EquipmentWarrantyLineRequest> equipmentWarrantyLines
) {
}
