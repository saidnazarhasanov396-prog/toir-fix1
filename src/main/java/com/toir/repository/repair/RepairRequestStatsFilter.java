package com.toir.repository.repair;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestStatsFilter(
        UUID departmentId,
        UUID equipmentId,
        String searchPattern,
        String priority,
        String status,
        String statusScope,
        String criticality,
        String source,
        Instant detectedAtFrom,
        Instant detectedAtTo,
        Instant targetCompletionAtFrom,
        Instant targetCompletionAtTo,
        Boolean hasLinkedDefects,
        Boolean hasLinkedWorkOrders
) {
    public static RepairRequestStatsFilter empty() {
        return new RepairRequestStatsFilter(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );
    }
}
