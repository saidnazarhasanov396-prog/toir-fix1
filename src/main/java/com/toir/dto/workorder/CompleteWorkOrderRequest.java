package com.toir.dto.workorder;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import jakarta.validation.Valid;
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
        List<CompletionMeterSnapshotRequest> meterSnapshots,
        List<@Valid RepairMaterialUsageDto> materialUsages,
        UUID repairActFileId,
        UUID stoppageActFileId,
        List<@Valid WorkOrderSparePartLifecycleOperation> sparePartLifecycleOperations
) {
        public CompleteWorkOrderRequest(
                String result,
                String summary,
                UUID oldEquipmentReturnWarehouseId
        ) {
                this(result, summary, oldEquipmentReturnWarehouseId, null, null, null, null, null, null, null,
                                null, null, null);
        }

        public CompleteWorkOrderRequest(
                String result,
                String summary,
                UUID oldEquipmentReturnWarehouseId,
                UUID regulationId,
                UUID equipmentMaintenanceRuleId,
                Instant performedAt,
                Instant plannedDueAt,
                MaintenanceRecalculationPolicy recalculationPolicy,
                List<CompletionMeterSnapshotRequest> meterSnapshots
        ) {
                this(result, summary, oldEquipmentReturnWarehouseId, regulationId, equipmentMaintenanceRuleId,
                        performedAt, plannedDueAt, recalculationPolicy, meterSnapshots, null, null, null, null);
        }

        public CompleteWorkOrderRequest(
                String result,
                String summary,
                UUID oldEquipmentReturnWarehouseId,
                UUID regulationId,
                UUID equipmentMaintenanceRuleId,
                Instant performedAt,
                Instant plannedDueAt,
                MaintenanceRecalculationPolicy recalculationPolicy,
                List<CompletionMeterSnapshotRequest> meterSnapshots,
                List<@Valid RepairMaterialUsageDto> materialUsages
        ) {
                this(result, summary, oldEquipmentReturnWarehouseId, regulationId, equipmentMaintenanceRuleId,
                        performedAt, plannedDueAt, recalculationPolicy, meterSnapshots, materialUsages, null, null, null);
        }

        public CompleteWorkOrderRequest(
                String result,
                String summary,
                UUID oldEquipmentReturnWarehouseId,
                UUID regulationId,
                UUID equipmentMaintenanceRuleId,
                Instant performedAt,
                Instant plannedDueAt,
                MaintenanceRecalculationPolicy recalculationPolicy,
                List<CompletionMeterSnapshotRequest> meterSnapshots,
                List<@Valid RepairMaterialUsageDto> materialUsages,
                UUID repairActFileId,
                UUID stoppageActFileId
        ) {
                this(result, summary, oldEquipmentReturnWarehouseId, regulationId, equipmentMaintenanceRuleId,
                        performedAt, plannedDueAt, recalculationPolicy, meterSnapshots, materialUsages,
                        repairActFileId, stoppageActFileId, null);
        }
}
