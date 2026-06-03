package com.toir.dto.workorder;

import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderRequest(
        @NotBlank String number,
        @NotBlank String title,
        @NotNull UUID equipmentId,
        UUID equipmentNodeId,
        @NotNull UUID departmentId,
        UUID repairRequestId,
        UUID defectId,
        UUID pprTaskId,
        UUID contractorId,
        @NotNull WorkOrderType type,
        WorkType workType,
        UUID warehouseId,
        UUID replacementEquipmentId,
        PriorityLevel priority,
        Instant startPlannedAt,
        Instant endPlannedAt,
        @NotNull UUID createdById,
        String summary,
        UUID maintenanceDueEventId,
        String cycleKey
) {
    public WorkOrderRequest(
            @NotBlank String number,
            @NotBlank String title,
            @NotNull UUID equipmentId,
            UUID equipmentNodeId,
            @NotNull UUID departmentId,
            UUID repairRequestId,
            UUID defectId,
            UUID pprTaskId,
            UUID contractorId,
            @NotNull WorkOrderType type,
            WorkType workType,
            UUID warehouseId,
            UUID replacementEquipmentId,
            PriorityLevel priority,
            Instant startPlannedAt,
            Instant endPlannedAt,
            @NotNull UUID createdById,
            String summary
    ) {
        this(number, title, equipmentId, equipmentNodeId, departmentId, repairRequestId, defectId, pprTaskId,
                contractorId, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, null, null);
    }

    public WorkOrderRequest(@NotBlank String number,
                            @NotBlank String title,
                            @NotNull UUID equipmentId,
                            @NotNull UUID departmentId,
                            UUID repairRequestId,
                            UUID defectId,
                            UUID pprTaskId,
                            UUID contractorId,
                            @NotNull WorkOrderType type,
                            WorkType workType,
                            UUID warehouseId,
                            UUID replacementEquipmentId,
                            PriorityLevel priority,
                            Instant startPlannedAt,
                            Instant endPlannedAt,
                            @NotNull UUID createdById,
                            String summary) {
        this(number, title, equipmentId, null, departmentId, repairRequestId, defectId, pprTaskId, contractorId, type,
                workType, warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, null, null);
    }
}
