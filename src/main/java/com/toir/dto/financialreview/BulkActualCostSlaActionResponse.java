package com.toir.dto.financialreview;

import java.util.List;
import java.util.UUID;

public record BulkActualCostSlaActionResponse(
        String action,
        int thresholdHours,
        int reminderWindowHours,
        int processed,
        int created,
        int skipped,
        int failed,
        List<Success> successes,
        List<Failure> failures
) {
    public record Success(UUID id, String result) {
    }

    public record Failure(UUID id, String message) {
    }
}
