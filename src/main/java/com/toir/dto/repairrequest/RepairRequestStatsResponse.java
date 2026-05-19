package com.toir.dto.repairrequest;

public record RepairRequestStatsResponse(
        long totalRequests,
        long emergency,
        long open,
        long withWorkOrder
) {
}
