package com.toir.dto.equipmentcost;

import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EquipmentCostDrilldownResponse(
        UUID equipmentId,
        String currency,
        double totalAmount,
        double approvedAmount,
        double pendingAmount,
        double rejectedAmount,
        List<Bucket> byCategory,
        List<Bucket> bySourceType,
        List<Bucket> byMonth,
        List<Row> rows
) {
    public record Bucket(
            String key,
            String label,
            double totalAmount,
            double approvedAmount,
            double pendingAmount,
            double rejectedAmount,
            long count
    ) {}

    public record CategoryRef(
            UUID id,
            String code,
            String name
    ) {}

    public record SourceLink(
            String targetType,
            UUID targetId,
            String path
    ) {}

    public record Row(
            UUID actualCostId,
            double amount,
            String currency,
            ActualCostStatus status,
            CategoryRef category,
            ActualCostSourceType sourceType,
            UUID sourceId,
            String sourceDisplay,
            SourceLink sourceLink,
            UUID workOrderId,
            UUID repairRequestId,
            UUID contractorWorkId,
            Instant createdAt,
            Instant approvedAt
    ) {}
}
