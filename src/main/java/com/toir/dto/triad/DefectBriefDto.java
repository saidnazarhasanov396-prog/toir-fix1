package com.toir.dto.triad;

import com.toir.enums.DefectStatus;

import java.time.Instant;
import java.util.UUID;

public record DefectBriefDto(
        UUID id,
        String code,
        String title,
        DefectStatus status,
        String severity,
        Instant createdAt
) {
}
