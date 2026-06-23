package com.toir.dto.notification;

import java.time.Instant;
import java.util.UUID;

public record FinancialReviewInboxAcknowledgementResponse(
        UUID id,
        Instant acknowledgedAt,
        String comment,
        boolean markedRead
) {
}
