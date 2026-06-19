package com.toir.dto.workorder;

import com.toir.enums.TaskExecutionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record WorkOrderTaskStatusUpdateRequest(
        @NotNull TaskExecutionStatus status,
        @PositiveOrZero Double actualHours
) {
}
