package com.toir.dto.notification;

import java.util.List;
import java.util.UUID;

public record BulkFinancialReviewInboxAcknowledgementResponse(
        int processed,
        int acknowledged,
        int failed,
        List<Acknowledgement> acknowledgements,
        List<Failure> failures
) {
    public record Acknowledgement(UUID id, boolean markedRead) {
    }

    public record Failure(UUID id, String message) {
    }
}
