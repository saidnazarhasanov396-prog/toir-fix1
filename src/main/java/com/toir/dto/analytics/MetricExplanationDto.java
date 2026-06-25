package com.toir.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Localized explanation for a calculated metric.")
public record MetricExplanationDto(
        @Schema(description = "Resolved response locale: uz, en, or ru.")
        String locale,
        @Schema(description = "Localized calculation formula.")
        String formula,
        @Schema(description = "Localized plain-language summary with calculated values.")
        String summary,
        @Schema(description = "Ordered dynamic calculation steps.")
        List<MetricExplanationStepDto> steps
) {
    public MetricExplanationDto {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
