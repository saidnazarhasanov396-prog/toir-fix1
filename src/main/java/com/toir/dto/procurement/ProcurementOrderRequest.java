package com.toir.dto.procurement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementOrderRequest(
        UUID counteragentId,
        LocalDate expectedDeliveryDate,
        String comment,
        List<EquipmentWarrantyLineRequest> equipmentWarrantyLines
) {
}
