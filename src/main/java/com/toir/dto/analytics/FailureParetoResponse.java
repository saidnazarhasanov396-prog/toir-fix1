package com.toir.dto.analytics;

import java.util.List;

public record FailureParetoResponse(List<AnalyticsOverview.FailureReasonRow> items) {
}
