package com.toir.dto.rcm.autoplan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RcmAutoPlanPreviewResponse(
        int riskThreshold,
        UUID targetPlanId,
        String targetPlanName,
        Instant calculatedAt,
        int candidates,
        int tasksToCreate,
        int duplicates,
        int conflicts,
        int skipped,
        String fingerprint,
        List<RcmAutoPlanPreviewRow> rows
) {
    public RcmAutoPlanPreviewResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
