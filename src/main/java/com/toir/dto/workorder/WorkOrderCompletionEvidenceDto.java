package com.toir.dto.workorder;

import com.toir.entity.WorkOrderCompletionEvidence;
import com.toir.enums.CompletionEvidenceType;
import java.time.Instant;
import java.util.UUID;

public record WorkOrderCompletionEvidenceDto(
        UUID id,
        CompletionEvidenceType type,
        UUID fileAssetId,
        Instant capturedAt,
        UUID submittedById,
        String note
) {
    public static WorkOrderCompletionEvidenceDto from(WorkOrderCompletionEvidence evidence) {
        return new WorkOrderCompletionEvidenceDto(
                evidence.getId(),
                evidence.getEvidenceType(),
                evidence.getFileAssetId(),
                evidence.getCapturedAt(),
                evidence.getSubmittedById(),
                evidence.getNote());
    }
}
