package com.toir.dto.repairacceptance;

import com.toir.enums.RepairAcceptanceDefectStatus;

import java.util.UUID;

public record RepairAcceptanceDefectRequest(
        UUID id,
        UUID defectId,
        String description,
        boolean critical,
        RepairAcceptanceDefectStatus status,
        String remarks
) {
}
