package com.toir.dto.repairacceptance;

import com.toir.entity.maintenance.RepairAcceptanceDefect;
import com.toir.enums.RepairAcceptanceDefectStatus;

import java.time.Instant;
import java.util.UUID;

public record RepairAcceptanceDefectDto(
        UUID id,
        UUID defectId,
        String description,
        boolean critical,
        RepairAcceptanceDefectStatus status,
        Instant resolvedAt,
        String remarks
) {
    public static RepairAcceptanceDefectDto from(RepairAcceptanceDefect defect) {
        return new RepairAcceptanceDefectDto(
                defect.getId(),
                defect.getDefectId(),
                defect.getDescription(),
                defect.isCritical(),
                defect.getStatus(),
                defect.getResolvedAt(),
                defect.getRemarks()
        );
    }
}
