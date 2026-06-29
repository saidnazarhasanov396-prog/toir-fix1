package com.toir.dto.warehouse;

import java.util.UUID;

public record WmsScanValidationRequest(
        String expectedType,
        UUID expectedId,
        UUID taskId,
        UUID taskLineId,
        String scannedPayload,
        String manualValue
) {
}
