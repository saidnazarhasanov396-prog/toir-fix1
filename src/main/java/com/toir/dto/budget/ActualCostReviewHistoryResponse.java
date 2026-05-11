package com.toir.dto.budget;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActualCostReviewHistoryResponse(
        String actualCostId,
        ActualCostRef actualCost,
        List<Event> events
) {
    public record ActualCostRef(
            UUID id,
            String status,
            Double amount,
            Instant costDate,
            Instant reviewedAt,
            UUID reviewedById,
            String reviewComment
    ) {
    }

    public record UserRef(
            UUID id,
            String fullName
    ) {
    }

    public record Event(
            UUID id,
            Instant reviewedAt,
            String status,
            UUID reviewedById,
            UserRef reviewedBy,
            String comment
    ) {
    }
}
