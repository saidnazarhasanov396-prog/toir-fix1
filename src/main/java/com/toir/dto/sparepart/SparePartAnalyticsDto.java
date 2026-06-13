package com.toir.dto.sparepart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SparePartAnalyticsDto(
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        BigDecimal totalIssuedQuantity,
        BigDecimal totalIssueAmount,
        List<MonthlyConsumptionDto> monthlyConsumption,
        BigDecimal averageMonthlyConsumption
) {
    public record MonthlyConsumptionDto(
            int year,
            int month,
            BigDecimal issuedQuantity,
            BigDecimal issueAmount
    ) {
    }
}
