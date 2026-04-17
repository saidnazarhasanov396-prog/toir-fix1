package com.toir.workexecution.dto;

import com.toir.workexecution.WorkExecution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionLogDto(
        UUID id,
        UUID workOrderId,
        UUID performerId,
        String notes,
        Instant startedAt,
        Instant endedAt,
        String result,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExecutionLogDto from(WorkExecution execution) {
        return new ExecutionLogDto(
                execution.getId(),
                execution.getWorkOrderId(),
                execution.getPerformerId(),
                execution.getNotes(),
                execution.getStartedAt(),
                execution.getEndedAt(),
                execution.getResult(),
                execution.getCreatedAt(),
                execution.getUpdatedAt()
        );
    }
}
