package com.toir.dto.workexecution;

import com.toir.entity.WorkExecution;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record WorkExecutionDto(
        UUID id,
        UUID workOrderId,
        UUID performerId,
        String notes,
        @NotNull Instant startedAt,
        Instant endedAt,
        String result
) {
    public static WorkExecutionDto from(WorkExecution e) {
        return new WorkExecutionDto(e.getId(), e.getWorkOrderId(), e.getPerformerId(), e.getNotes(),
                e.getStartedAt(), e.getEndedAt(), e.getResult());
    }
}
