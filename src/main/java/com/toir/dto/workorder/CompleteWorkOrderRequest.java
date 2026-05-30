package com.toir.dto.workorder;

import jakarta.validation.constraints.NotBlank;

import com.toir.enums.MaintenanceRecalculationPolicy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompleteWorkOrderRequest(
        @NotBlank String result,
        String summary,
        UUID oldEquipmentReturnWarehouseId,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        Instant performedAt,
        Instant plannedDueAt,
        MaintenanceRecalculationPolicy recalculationPolicy,
        List<CompletionMeterSnapshotRequest> meterSnapshots
) {
        public CompleteWorkOrderRequest(
                String result,
                String summary,
                UUID oldEquipmentReturnWarehouseId
        ) {
                this(result, summary, oldEquipmentReturnWarehouseId, null, null, null, null, null, null);
        }
}
