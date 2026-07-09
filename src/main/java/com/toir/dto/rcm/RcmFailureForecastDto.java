package com.toir.dto.rcm;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "User-facing forecast for when failure risk may become actionable.")
public record RcmFailureForecastDto(
        String status,
        String label,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expectedFailureAt,
        Double remainingHours,
        Double mtbfHours,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastFailureAt,
        String basis,
        String confidence
) {
}
