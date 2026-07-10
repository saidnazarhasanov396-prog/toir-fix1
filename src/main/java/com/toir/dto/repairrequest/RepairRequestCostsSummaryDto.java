package com.toir.dto.repairrequest;

import java.util.List;
import java.util.UUID;

public record RepairRequestCostsSummaryDto(
        UUID requestId,
        String currency,
        double totalCost,
        double laborCost,
        double contractorCost,
        double materialCost,
        List<RepairRequestCostRowDto> rows
) {
    public RepairRequestCostsSummaryDto {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
