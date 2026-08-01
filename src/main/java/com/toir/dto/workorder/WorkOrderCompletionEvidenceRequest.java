package com.toir.dto.workorder;

import com.toir.enums.CompletionEvidenceType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record WorkOrderCompletionEvidenceRequest(
        @NotNull CompletionEvidenceType type,
        UUID fileAssetId,
        Instant capturedAt,
        String note
) {
}
