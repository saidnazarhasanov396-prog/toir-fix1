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
        UUID counteragentId,
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
        String cycleKey,
        Boolean repairActRequired,
        Boolean stoppageActRequired,
        UUID repairCampaignId,
        UUID repairCampaignStageId,
        UUID budgetLineId,
        Boolean requiresShutdown,
        Boolean requiresIsolation,
        String generationKey
) {
    public WorkOrderRequest {
        requiresShutdown = Boolean.TRUE.equals(requiresShutdown);
        requiresIsolation = Boolean.TRUE.equals(requiresIsolation);
    }

    public WorkOrderRequest(
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
            UUID counteragentId,
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
            String cycleKey,
            Boolean repairActRequired,
            Boolean stoppageActRequired,
            UUID repairCampaignId,
            UUID repairCampaignStageId,
            UUID budgetLineId
    ) {
        this(number, title, equipmentId, equipmentNodeId, locationId, departmentId, workLocationNote,
                repairRequestId, defectId, defectListId, pprTaskId, counteragentId, performerId, type, workType,
                warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, maintenanceDueEventId, cycleKey, repairActRequired, stoppageActRequired,
                repairCampaignId, repairCampaignStageId, budgetLineId, null, null, null);
    }

    public WorkOrderRequest(
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
            UUID counteragentId,
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
            String cycleKey,
            Boolean repairActRequired,
            Boolean stoppageActRequired
    ) {
        this(number, title, equipmentId, equipmentNodeId, locationId, departmentId, workLocationNote,
                repairRequestId, defectId, defectListId, pprTaskId, counteragentId, performerId, type, workType,
                warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, maintenanceDueEventId, cycleKey, repairActRequired, stoppageActRequired,
                null, null, null, null, null, null);
    }

    public WorkOrderRequest(
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
            UUID counteragentId,
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
            String cycleKey,
            Boolean repairActRequired,
            Boolean stoppageActRequired,
            UUID repairCampaignId,
            UUID repairCampaignStageId
    ) {
        this(number, title, equipmentId, equipmentNodeId, locationId, departmentId, workLocationNote,
                repairRequestId, defectId, defectListId, pprTaskId, counteragentId, performerId, type, workType,
                warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, maintenanceDueEventId, cycleKey, repairActRequired, stoppageActRequired,
                repairCampaignId, repairCampaignStageId, null, null, null, null);
    }

    public WorkOrderRequest(
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
            UUID counteragentId,
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
        this(number, title, equipmentId, equipmentNodeId, locationId, departmentId, workLocationNote,
                repairRequestId, defectId, defectListId, pprTaskId, counteragentId, performerId, type, workType,
                warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, maintenanceDueEventId, cycleKey, null, null, null, null, null, null, null, null);
    }

    public WorkOrderRequest(
            String number,
            @NotBlank String title,
            @NotNull UUID equipmentId,
            UUID equipmentNodeId,
            UUID departmentId,
            UUID repairRequestId,
            UUID defectId,
            UUID pprTaskId,
            UUID counteragentId,
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
                null, pprTaskId, counteragentId, performerId, type, workType, warehouseId, replacementEquipmentId, priority,
                startPlannedAt, endPlannedAt, createdById, summary, maintenanceDueEventId, cycleKey,
                null, null, null, null, null, null, null, null);
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
            UUID counteragentId,
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
                counteragentId, null, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, maintenanceDueEventId, cycleKey,
                null, null, null, null, null, null, null, null);
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
            UUID counteragentId,
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
                counteragentId, null, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, null, null, null, null, null, null, null, null, null, null);
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
            UUID counteragentId,
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
                counteragentId, performerId, type, workType, warehouseId, replacementEquipmentId, priority, startPlannedAt,
                endPlannedAt, createdById, summary, null, null, null, null, null, null, null, null, null, null);
    }

    public WorkOrderRequest(String number,
                            @NotBlank String title,
                            @NotNull UUID equipmentId,
                            @NotNull UUID departmentId,
                            UUID repairRequestId,
                            UUID defectId,
                            UUID pprTaskId,
                            UUID counteragentId,
                            @NotNull WorkOrderType type,
                            WorkType workType,
                            UUID warehouseId,
                            UUID replacementEquipmentId,
                            PriorityLevel priority,
                            Instant startPlannedAt,
                            Instant endPlannedAt,
                            UUID createdById,
                            String summary) {
        this(number, title, equipmentId, null, null, departmentId, null, repairRequestId, defectId, null, pprTaskId, counteragentId, null, type,
                workType, warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById,
                summary, null, null, null, null, null, null, null, null, null, null);
    }

    public WorkOrderRequest withRepairCampaign(UUID repairCampaignId, UUID repairCampaignStageId) {
        return new WorkOrderRequest(
                number,
                title,
                equipmentId,
                equipmentNodeId,
                locationId,
                departmentId,
                workLocationNote,
                repairRequestId,
                defectId,
                defectListId,
                pprTaskId,
                counteragentId,
                performerId,
                type,
                workType,
                warehouseId,
                replacementEquipmentId,
                priority,
                startPlannedAt,
                endPlannedAt,
                createdById,
                summary,
                maintenanceDueEventId,
                cycleKey,
                repairActRequired,
                stoppageActRequired,
                repairCampaignId,
                repairCampaignStageId,
                budgetLineId,
                requiresShutdown,
                requiresIsolation,
                generationKey
        );
    }

    public WorkOrderRequest withGenerationKey(String generationKey) {
        return new WorkOrderRequest(
                number, title, equipmentId, equipmentNodeId, locationId, departmentId, workLocationNote,
                repairRequestId, defectId, defectListId, pprTaskId, counteragentId, performerId, type, workType,
                warehouseId, replacementEquipmentId, priority, startPlannedAt, endPlannedAt, createdById, summary,
                maintenanceDueEventId, cycleKey, repairActRequired, stoppageActRequired, repairCampaignId,
                repairCampaignStageId, budgetLineId, requiresShutdown, requiresIsolation, generationKey
        );
    }
}
