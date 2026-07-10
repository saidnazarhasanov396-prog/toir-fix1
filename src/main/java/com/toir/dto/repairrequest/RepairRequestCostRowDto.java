package com.toir.dto.repairrequest;

import com.toir.enums.ActualCostStatus;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestCostRowDto(
        UUID id,
        RepairRequestCostKind kind,
        String sourceLabel,
        UUID workOrderId,
        String workOrderNumber,
        ActualCostStatus status,
        double amount,
        Instant costDate
) {
}
