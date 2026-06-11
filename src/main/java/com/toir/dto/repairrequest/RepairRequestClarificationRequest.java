package com.toir.dto.repairrequest;

import java.util.UUID;

public record RepairRequestClarificationRequest(
        UUID recipientUserId,
        String message,
        String sourceAction
) {
}
