package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparePartLifecycleItem(
        UUID installationId,
        UUID equipmentId,
        UUID sparePartId,
        String name,
        String code,
        String serialNumber,
        String manufacturer,
        String model,
        boolean active,
        Instant installedAt,
        Instant removedAt,
        String ruleType,
        String usageUnit,
        BigDecimal limitValue,
        BigDecimal currentValue,
        BigDecimal remainingValue,
        SparePartLifecycleEvaluationState status,
        boolean hasEvaluationError,
        boolean acknowledged,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        SparePartDueAction nextAction,
        UUID dueEventId,
        SparePartDueEventState dueEventState,
        List<LinkedWorkOrderSummary> linkedWorkOrders,
        List<String> availableActions,
        BigDecimal warehouseAvailability
) {
    public record LinkedWorkOrderSummary(UUID workOrderId, String linkStatus, Instant linkedAt) { }
}
