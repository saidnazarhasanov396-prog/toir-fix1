package com.toir.dto.defect;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;

import java.time.Instant;
import java.util.UUID;

public record DefectDto(
        UUID id,
        String code,
        String title,
        String description,
        UUID equipmentId,
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
    public static DefectDto from(Defect d) {
        return new DefectDto(
                d.getId(), d.getCode(), d.getTitle(), d.getDescription(),
                d.getEquipmentId(), d.getRequestId(), d.getWorkOrderId(),
                d.getCategory(), d.getSeverity(), d.getFailureReason(), d.getRootCause(),
                d.getStatus(), d.getDetectedAt(), d.getResolvedAt(), d.getRecurrenceCount()
        );
    }
}
