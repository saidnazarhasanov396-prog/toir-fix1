package com.toir.dto.budget;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActualCostReviewHistoryResponse(
        String actualCostId,
        List<Event> events
) {
    public record Event(
            Instant reviewedAt,
            String status,
            UUID reviewedById,
            String comment
    ) {
    }
}
