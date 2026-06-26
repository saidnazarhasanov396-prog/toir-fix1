package com.toir.dto.rcm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One evidence-backed reason contributing to the RCM risk score.")
public record RiskReasonDto(
        @Schema(description = "Stable machine-readable reason code.")
        RiskReasonCode code,
        @Schema(description = "Whether the reason affects probability or consequence.")
        RiskReasonCategory category,
        @Schema(description = "Localized display label.")
        String label,
        @Schema(description = "Raw value observed in backend evidence.")
        Object value,
        @Schema(description = "Localized description of the scoring effect.")
        String effect,
        @Schema(description = "Relative severity of this reason.")
        RiskSeverity severity
) {
}
