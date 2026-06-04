package com.toir.dto.sparepartforecast;

public record SparePartForecastEvaluateResponse(
        int createdIssueCount,
        int updatedIssueCount,
        int resolvedIssueCount,
        SparePartForecastSummaryDto summary
) {
}
