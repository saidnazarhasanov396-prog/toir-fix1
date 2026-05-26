package com.toir.dto.pprplanning;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.enums.PriorityLevel;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.enums.PprTaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PprTaskDto(
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
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate endDate,
        LocalDateTime dueDate,
        PprTaskStatus status,
        PriorityLevel priority,
        double plannedLaborHours,
        Double actualLaborHours,
        String postponeReason
) {
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
                equipmentId,
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
                postponeReason
        );
    }

    public static PprTaskDto from(PprTask t) {
        return from(t, Map.of());
    }

    public static PprTaskDto from(PprTask t, Map<UUID, EquipmentMaintenanceRule> ruleById) {
        EquipmentMaintenanceRule rule = t.getEquipmentMaintenanceRuleId() == null
                ? null
                : ruleById.get(t.getEquipmentMaintenanceRuleId());
        return new PprTaskDto(
                t.getId(), t.getCode(), t.getPlan().getId(), t.getRegulationId(),
                t.getEquipmentMaintenanceRuleId(),
                rule != null ? rule.getCode() : null,
                rule != null ? rule.getName() : null,
                t.getEquipmentId(),
                t.getTitle(), t.getScheduledStart(), t.getScheduledEnd(),
                t.getScheduledStart() != null ? t.getScheduledStart().toLocalDate() : null,
                t.getScheduledEnd() != null ? t.getScheduledEnd().toLocalDate() : null,
                t.getDueDate(),
                t.getStatus(), t.getPriority(), t.getPlannedLaborHours(), t.getActualLaborHours(),
                t.getPostponeReason()
        );
    }
}
