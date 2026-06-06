package com.toir.dto.workorder;

import com.toir.enums.TaskExecutionStatus;
import com.toir.entity.maintenance.WorkOrderTask;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderTaskDto(
        UUID id,
        String title,
        String description,
        TaskExecutionStatus status,
        UUID assignedToId,
        Double plannedHours,
        Double actualHours,
        UUID sourceTemplateId,
        UUID sourceOperationId,
        Instant startedAt,
        Instant completedAt
) {
    public WorkOrderTaskDto(UUID id,
                            String title,
                            String description,
                            TaskExecutionStatus status,
                            UUID assignedToId,
                            Double plannedHours,
                            Double actualHours,
                            Instant startedAt,
                            Instant completedAt) {
        this(id, title, description, status, assignedToId, plannedHours, actualHours, null, null,
                startedAt, completedAt);
    }

    public static WorkOrderTaskDto from(WorkOrderTask t) {
        return new WorkOrderTaskDto(
                t.getId(), t.getTitle(), t.getDescription(), t.getStatus(),
                t.getAssignedToId(), t.getPlannedHours(), t.getActualHours(),
                t.getSourceTemplateId(), t.getSourceOperationId(),
                t.getStartedAt(), t.getCompletedAt()
        );
    }
}
