package com.toir.dto.workorder;

import com.toir.enums.TaskExecutionStatus;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
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
        String sourceTemplateCode,
        String sourceTemplateName,
        UUID sourceOperationId,
        String sourceOperationCode,
        String sourceOperationName,
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
        this(id, title, description, status, assignedToId, plannedHours, actualHours, null, null, null,
                null, null, null, startedAt, completedAt);
    }

    public WorkOrderTaskDto(UUID id,
                            String title,
                            String description,
                            TaskExecutionStatus status,
                            UUID assignedToId,
                            Double plannedHours,
                            Double actualHours,
                            UUID sourceTemplateId,
                            UUID sourceOperationId,
                            Instant startedAt,
                            Instant completedAt) {
        this(id, title, description, status, assignedToId, plannedHours, actualHours,
                sourceTemplateId, null, null, sourceOperationId, null, null,
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

    public static WorkOrderTaskDto from(WorkOrderTask t,
                                        MaintenanceTemplate template,
                                        MaintenanceOperation operation) {
        String operationCode = operation != null && operation.getAction() != null
                ? operation.getAction().getCode()
                : null;
        String operationName = operation == null ? null : operation.getName();
        return new WorkOrderTaskDto(
                t.getId(), t.getTitle(), t.getDescription(), t.getStatus(),
                t.getAssignedToId(), t.getPlannedHours(), t.getActualHours(),
                t.getSourceTemplateId(),
                template == null ? null : template.getCode(),
                template == null ? null : template.getName(),
                t.getSourceOperationId(),
                operationCode,
                operationName,
                t.getStartedAt(), t.getCompletedAt()
        );
    }
}
