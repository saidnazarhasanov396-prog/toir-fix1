package com.toir.dto.warehouse;

import java.util.UUID;

public record WmsScanValidationResultDto(
        boolean valid,
        String expectedType,
        UUID expectedId,
        String scannedType,
        UUID scannedId,
        String manualValue,
        String message
) {
}
