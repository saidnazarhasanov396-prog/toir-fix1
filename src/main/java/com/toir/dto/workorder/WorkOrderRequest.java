package com.toir.dto.workorder;

import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderRequest(
        String number,
        @NotBlank String title,
        @NotNull UUID equipmentId,
        UUID equipmentNodeId,
        UUID locationId,
        UUID departmentId,
        String workLocationNote,
        UUID repairRequestId,
        UUID defectId,
        UUID defectListId,
        UUID pprTaskId,
        UUID contractorId,
        UUID performerId,
        @NotNull WorkOrderType type,
        WorkType workType,
        UUID warehouseId,
        UUID replacementEquipmentId,
        PriorityLevel priority,
        Instant startPlannedAt,
        Instant endPlannedAt,
        UUID createdById,
        String summary,
        UUID maintenanceDueEventId,
        String cycleKey
) {
    public WorkOrderRequest(
            String number,
            @NotBlank String title,
            @NotNull UUID equipmentId,
            UUID equipmentNodeId,
            UUID departmentId,
            UUID repairRequestId,
            UUID defectId,
            UUID pprTaskId,
            UUID contractorId,
            UUID performerId,
            @NotNull WorkOrderType type,
            WorkType workType,
            UUID warehouseId,
            UUID replacementEquipmentId,
            PriorityLevel priority,
            Instant startPlannedAt,
            Instant endPlannedAt,
            UUID createdById,
            String summary,
            UUID maintenanceDueEventId,
            String cycleKey
    ) {
        this(number, title, equipmentId, equipmentNodeId, null, departmentId, null, repairRequestId, defectId,
                null, pprTaskId, contractorId, performerId, type, workType, warehouseId, replacementEquipmentId, priority,
                startPlannedAt, endPlannedAt, createdById, summary, maintenanceDueEventId, cycleKey);
    }

    public WorkOrderRequest(
            String number,
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
            UUID createdById,
            String summary,
            UUID maintenanceDueEventId,
            String cycleKey
    ) {
        this(number, title, equipmentId, equipmentNodeId, null, departmentId, null, repairRequestId, defectId, null, pprTaskId,
                contractorId, null, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, maintenanceDueEventId, cycleKey);
    }

    public WorkOrderRequest(
            String number,
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
            UUID createdById,
            String summary
    ) {
        this(number, title, equipmentId, equipmentNodeId, null, departmentId, null, repairRequestId, defectId, null, pprTaskId,
                contractorId, null, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, null, null);
    }

    public WorkOrderRequest(
            String number,
            @NotBlank String title,
            @NotNull UUID equipmentId,
            UUID equipmentNodeId,
            @NotNull UUID departmentId,
            UUID repairRequestId,
            UUID defectId,
            UUID pprTaskId,
            UUID contractorId,
            UUID performerId,
            @NotNull WorkOrderType type,
            WorkType workType,
            UUID warehouseId,
            UUID replacementEquipmentId,
            PriorityLevel priority,
            Instant startPlannedAt,
            Instant endPlannedAt,
            UUID createdById,
            String summary
    ) {
        this(number, title, equipmentId, equipmentNodeId, null, departmentId, null, repairRequestId, defectId, null, pprTaskId,
                contractorId, performerId, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, null, null);
    }

    public WorkOrderRequest(String number,
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
                            UUID createdById,
                            String summary) {
        this(number, title, equipmentId, null, null, departmentId, null, repairRequestId, defectId, null, pprTaskId, contractorId, null, type,
                workType, warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, null, null);
    }
}
