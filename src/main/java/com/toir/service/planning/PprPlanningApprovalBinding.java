package com.toir.service.planning;

import java.util.UUID;

public record PprPlanningApprovalBinding(
        UUID sessionId,
        UUID variantId,
        long revision,
        String contentHash,
        int hashVersion) {
}
