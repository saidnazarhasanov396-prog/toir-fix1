package com.toir.dto.triad;

import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;

import java.util.UUID;

public record RepairRequestBriefDto(
        UUID id,
        String number,
        RequestStatus status,
        PriorityLevel priority,
        String title,
        String description
) {
}
