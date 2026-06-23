package com.toir.dto.financialreview;

import java.util.List;
import java.util.UUID;

public record BulkRouteOverrideClearResponse(
        String action,
        int processed,
        int succeeded,
        int failed,
        List<Success> successes,
        List<Failure> failures
) {
    public record Success(UUID id, List<UUID> clearedOverrideIds) {
    }

    public record Failure(UUID id, String message) {
    }
}
