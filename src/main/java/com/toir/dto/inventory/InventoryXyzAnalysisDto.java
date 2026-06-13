package com.toir.dto.inventory;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryXyzAnalysisDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        String classification,
        BigDecimal averageMonthlyDemand,
        BigDecimal demandStandardDeviation,
        BigDecimal coefficientOfVariation
) {
}
