package com.toir.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One dynamic calculation step used to explain a calculated metric.")
public record MetricExplanationStepDto(
        @Schema(description = "Localized label for the calculation component.")
        String label,
        @Schema(description = "Numeric value used in the calculation.")
        Number value,
        @Schema(description = "Optional localized unit or suffix.")
        String unit
) {
    public MetricExplanationStepDto(String label, Number value) {
        this(label, value, null);
    }
}
