package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryAbcAnalysisDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        String classification,
        BigDecimal annualConsumptionValue
) {
}
