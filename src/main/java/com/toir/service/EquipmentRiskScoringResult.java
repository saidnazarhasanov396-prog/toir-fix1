package com.toir.service;

import java.util.List;

public record EquipmentRiskScoringResult(
        int consequenceScore,
        int probabilityScore,
        int riskScore,
        List<EquipmentRiskScoringReason> reasons,
        EquipmentRiskScoringReason primaryReason
) {
    public EquipmentRiskScoringResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
