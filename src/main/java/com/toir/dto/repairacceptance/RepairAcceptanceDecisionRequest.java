package com.toir.dto.repairacceptance;

import com.toir.enums.RepairAcceptanceQualityGrade;

import java.util.UUID;

public record RepairAcceptanceDecisionRequest(
        UUID acceptedById,
        RepairAcceptanceQualityGrade qualityGrade,
        String remarks
) {
}
