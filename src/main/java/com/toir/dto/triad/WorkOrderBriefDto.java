package com.toir.dto.triad;

import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderBriefDto(
        UUID id,
        String number,
        String title,
        String description,
        String assigneeName,
        WorkOrderStatus status,
        WorkType workType,
        PriorityLevel priority,
        Instant createdAt,
        Instant startPlannedAt,
        Instant endPlannedAt,
        Instant completedAt
) {
}
