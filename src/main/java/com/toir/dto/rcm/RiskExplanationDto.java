package com.toir.dto.rcm;

import com.toir.dto.analytics.MetricExplanationStepDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Localized, structured explanation for an RCM risk score.")
public record RiskExplanationDto(
        @Schema(description = "Resolved response locale: uz, en, or ru.")
        String locale,
        @Schema(description = "Localized calculation formula.")
        String formula,
        @Schema(description = "Localized plain-language summary with the primary reason.")
        String summary,
        @Schema(description = "Evidence-backed reasons behind the score.")
        List<RiskReasonDto> reasons,
        @Schema(description = "Ordered dynamic calculation steps.")
        List<MetricExplanationStepDto> steps
) {
    public RiskExplanationDto {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
