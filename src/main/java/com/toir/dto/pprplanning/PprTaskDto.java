package com.toir.dto.pprplanning;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.enums.PriorityLevel;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.CompletionEvidenceType;
import com.toir.enums.WorkOrderStatus;
import com.toir.entity.maintenance.WorkOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PprTaskDto(
        UUID id,
        String code,
        UUID planId,
        UUID regulationId,
        String regulationName,
        UUID equipmentMaintenanceRuleId,
        String equipmentMaintenanceRuleCode,
        String equipmentMaintenanceRuleName,
        UUID equipmentId,
        String equipmentName,
        String title,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate endDate,
        LocalDateTime dueDate,
        PprTaskStatus status,
        PriorityLevel priority,
        double plannedLaborHours,
        Double actualLaborHours,
        String postponeReason,
        int workOrderLeadDays,
        Set<CompletionEvidenceType> requiredEvidenceTypes,
        LocalDateTime workOrderGenerationAt,
        UUID workOrderId,
        String workOrderNumber,
        WorkOrderStatus workOrderStatus,
        boolean materializedAnnual
) {
    public PprTaskDto(
            UUID id,
            String code,
            UUID planId,
            UUID regulationId,
            String regulationName,
            UUID equipmentMaintenanceRuleId,
            String equipmentMaintenanceRuleCode,
            String equipmentMaintenanceRuleName,
            UUID equipmentId,
            String equipmentName,
            String title,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime dueDate,
            PprTaskStatus status,
            PriorityLevel priority,
            double plannedLaborHours,
            Double actualLaborHours,
            String postponeReason
    ) {
        this(
                id, code, planId, regulationId, regulationName,
                equipmentMaintenanceRuleId, equipmentMaintenanceRuleCode, equipmentMaintenanceRuleName,
                equipmentId, equipmentName, title, scheduledStart, scheduledEnd, startDate, endDate, dueDate,
                status, priority, plannedLaborHours, actualLaborHours, postponeReason,
                7, Set.of(), null, null, null, null, false
        );
    }

    public PprTaskDto(
            UUID id,
            String code,
            UUID planId,
            UUID regulationId,
            UUID equipmentId,
            String title,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime dueDate,
            PprTaskStatus status,
            PriorityLevel priority,
            double plannedLaborHours,
            Double actualLaborHours,
            String postponeReason
    ) {
        this(
                id,
                code,
                planId,
                regulationId,
                null,
                null,
                null,
                null,
                equipmentId,
                null,
                title,
                scheduledStart,
                scheduledEnd,
                startDate,
                endDate,
                dueDate,
                status,
                priority,
                plannedLaborHours,
                actualLaborHours,
                postponeReason,
                7, Set.of(), null, null, null, null, false
        );
    }

    public static PprTaskDto from(PprTask t) {
        return from(t, Map.of());
    }

    public static PprTaskDto from(PprTask t, Map<UUID, EquipmentMaintenanceRule> ruleById) {
        return from(t, ruleById, Map.of(), Map.of(), Map.of());
    }

    public static PprTaskDto from(PprTask t,
                                  Map<UUID, EquipmentMaintenanceRule> ruleById,
                                  Map<UUID, String> equipmentNames,
                                  Map<UUID, String> regulationNames) {
        return from(t, ruleById, equipmentNames, regulationNames, Map.of());
    }

    public static PprTaskDto from(PprTask t,
                                  Map<UUID, EquipmentMaintenanceRule> ruleById,
                                  Map<UUID, String> equipmentNames,
                                  Map<UUID, String> regulationNames,
                                  Map<UUID, WorkOrder> workOrderByTaskId) {
        EquipmentMaintenanceRule rule = t.getEquipmentMaintenanceRuleId() == null
                ? null
                : ruleById.get(t.getEquipmentMaintenanceRuleId());
        WorkOrder workOrder = workOrderByTaskId.get(t.getId());
        int leadDays = t.getWorkOrderLeadDays();
        return new PprTaskDto(
                t.getId(), t.getCode(), t.getPlan().getId(), t.getRegulationId(),
                t.getRegulationId() != null ? regulationNames.get(t.getRegulationId()) : null,
                t.getEquipmentMaintenanceRuleId(),
                rule != null ? rule.getCode() : null,
                rule != null ? rule.getName() : null,
                t.getEquipmentId(),
                t.getEquipmentId() != null ? equipmentNames.get(t.getEquipmentId()) : null,
                t.getTitle(), t.getScheduledStart(), t.getScheduledEnd(),
                t.getScheduledStart() != null ? t.getScheduledStart().toLocalDate() : null,
                t.getScheduledEnd() != null ? t.getScheduledEnd().toLocalDate() : null,
                t.getDueDate(),
                t.getStatus(), t.getPriority(), t.getPlannedLaborHours(), t.getActualLaborHours(),
                t.getPostponeReason(),
                leadDays,
                t.getRequiredEvidenceTypes() == null ? Set.of() : Set.copyOf(t.getRequiredEvidenceTypes()),
                t.getScheduledStart() == null ? null : t.getScheduledStart().minusDays(leadDays),
                workOrder == null ? null : workOrder.getId(),
                workOrder == null ? null : workOrder.getNumber(),
                workOrder == null ? null : workOrder.getStatus(),
                t.getSourceCalculationItemId() != null || t.getSourceVariantItemId() != null
        );
    }

    public PprTaskDto(
            UUID id,
            String code,
            UUID planId,
            UUID regulationId,
            UUID equipmentMaintenanceRuleId,
            String equipmentMaintenanceRuleCode,
            String equipmentMaintenanceRuleName,
            UUID equipmentId,
            String title,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime dueDate,
            PprTaskStatus status,
            PriorityLevel priority,
            double plannedLaborHours,
            Double actualLaborHours,
            String postponeReason
    ) {
        this(
                id,
                code,
                planId,
                regulationId,
                null,
                equipmentMaintenanceRuleId,
                equipmentMaintenanceRuleCode,
                equipmentMaintenanceRuleName,
                equipmentId,
                null,
                title,
                scheduledStart,
                scheduledEnd,
                startDate,
                endDate,
                dueDate,
                status,
                priority,
                plannedLaborHours,
                actualLaborHours,
                postponeReason,
                7, Set.of(), null, null, null, null, false
        );
    }
}
