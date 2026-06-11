package com.toir.dto.maintenancedueforecast;

import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MaintenanceDueForecastResponse(
        LocalDate horizonFrom,
        LocalDate horizonTo,
        Counters counters,
        Buckets buckets,
        List<Row> rows
) {
    public record Counters(
            long upcomingCount,
            long dueCount,
            long overdueCount,
            long blockedCount,
            long approvalPendingCount,
            long woCreatedCount
    ) {}

    public record Buckets(
            List<Row> overdue,
            List<Row> next7Days,
            List<Row> next30Days,
            List<Row> next90Days,
            List<Row> blocked
    ) {}

    public record Ref(
            UUID id,
            String code,
            String name
    ) {}

    public record Row(
            UUID dueEventId,
            UUID equipmentId,
            String equipmentCode,
            String equipmentName,
            Ref equipmentType,
            Ref department,
            MaintenanceDueStatus status,
            Instant dueDate,
            Long remainingDays,
            MeterType meterType,
            Double currentMeterValue,
            Double remainingMeterValue,
            MaintenanceTriggerPolicy triggerPolicy,
            String structuredExplanationSummary,
            String blockingCode,
            String blockingField,
            String fixLink,
            UUID createdWorkOrderId,
            List<String> allowedActions
    ) {}
}
