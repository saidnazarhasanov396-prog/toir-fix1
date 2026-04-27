package com.toir.dto.defect;

import java.time.Instant;
import java.util.UUID;

import com.toir.enums.DefectStatus;

public record DefectResponse(
        UUID id,
        String code,
        String title,
        String description,
        UUID equipmentId,
        String equipmentName,
        UUID requestId,
        UUID workOrderId,
        String category,
        String severity,
        String failureReason,
        String rootCause,
        DefectStatus status,
        Instant detectedAt,
        Instant resolvedAt,
        int recurrenceCount
) {
    public static DefectResponse from(DefectDto dto, String equipmentName) {
        return new DefectResponse(
                dto.id(),
                dto.code(),
                dto.title(),
                dto.description(),
                dto.equipmentId(),
                equipmentName,
                dto.requestId(),
                dto.workOrderId(),
                dto.category(),
                dto.severity(),
                dto.failureReason(),
                dto.rootCause(),
                dto.status(),
                dto.detectedAt(),
                dto.resolvedAt(),
                dto.recurrenceCount()
        );
    }
}
