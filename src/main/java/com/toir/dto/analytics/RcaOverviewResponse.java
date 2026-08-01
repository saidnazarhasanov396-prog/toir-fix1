package com.toir.dto.analytics;

import java.util.List;

public record RcaOverviewResponse(
        List<CountRow> topRootCauses,
        List<CountRow> topCauses,
        List<FailureChain> failureChains,
        List<PredictiveCandidate> predictiveCandidates,
        List<EquipmentRanking> equipmentRanking,
        List<CountRow> categoryBreakdown,
        AnalyticsContextDto analyticsContext
) {
    public RcaOverviewResponse(List<CountRow> topRootCauses, List<CountRow> topCauses,
                               List<FailureChain> failureChains,
                               List<PredictiveCandidate> predictiveCandidates,
                               List<EquipmentRanking> equipmentRanking,
                               List<CountRow> categoryBreakdown) {
        this(topRootCauses, topCauses, failureChains, predictiveCandidates,
                equipmentRanking, categoryBreakdown, null);
    }
    public record CountRow(String code, long count) {
    }

    public record FailureChain(String failureReason, String rootCause, long count) {
    }

    public record PredictiveCandidate(String equipmentId, double riskScore, String recommendation) {
    }

    public record EquipmentRanking(String equipmentId, long count) {
    }
}
