package com.toir.dto.financialreview;

import java.util.List;
import java.util.UUID;

public record BulkRouteOverrideApplyResponse(
        String action,
        int processed,
        int succeeded,
        int failed,
        List<Success> successes,
        List<Failure> failures
) {
    public record Success(UUID id, UUID overrideId) {
    }

    public record Failure(UUID id, String message) {
    }
}
