package com.toir.dto.rcm.autoplan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record RcmAutoPlanConfirmRequest(
        @Min(1) @Max(100) int riskThreshold,
        UUID planId,
        @NotBlank String previewFingerprint
) {
}
