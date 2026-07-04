package com.toir.dto.rcm;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "User-facing forecast for when failure risk may become actionable.")
public record RcmFailureForecastDto(
        String status,
        String label,
        Instant expectedFailureAt,
        Double remainingHours,
        Double mtbfHours,
        Instant lastFailureAt,
        String basis,
        String confidence
) {
}
