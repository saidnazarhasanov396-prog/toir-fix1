package com.toir.dto.budget;

import java.util.List;

public record ContractorWorkRecommendationResponse(
        String contractorWorkId,
        double recommendedAmount,
        List<Candidate> candidates
) {
    public record Candidate(String type, String reason, double amount) {
    }
}
