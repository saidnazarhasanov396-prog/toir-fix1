package com.toir.pprplanning.dto;

import com.toir.common.enums.PriorityLevel;
import com.toir.pprplanning.PprTask;
import com.toir.pprplanning.PprTaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PprTaskDto(
        UUID id,
        String code,
        UUID planId,
        UUID regulationId,
        UUID equipmentId,
        String title,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime dueDate,
        PprTaskStatus status,
        PriorityLevel priority,
        double plannedLaborHours,
        Double actualLaborHours,
        String postponeReason
) {
    public static PprTaskDto from(PprTask t) {
        return new PprTaskDto(
                t.getId(), t.getCode(), t.getPlan().getId(), t.getRegulationId(), t.getEquipmentId(),
                t.getTitle(), t.getScheduledStart(), t.getScheduledEnd(), t.getDueDate(),
                t.getStatus(), t.getPriority(), t.getPlannedLaborHours(), t.getActualLaborHours(),
                t.getPostponeReason()
        );
    }
}
