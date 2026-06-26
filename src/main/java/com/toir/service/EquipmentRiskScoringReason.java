package com.toir.service;

import com.toir.dto.rcm.RiskReasonCategory;
import com.toir.dto.rcm.RiskReasonCode;
import com.toir.dto.rcm.RiskSeverity;

public record EquipmentRiskScoringReason(
        RiskReasonCode code,
        RiskReasonCategory category,
        Object value,
        int scoreImpact,
        int targetScore,
        RiskSeverity severity
) {
}
