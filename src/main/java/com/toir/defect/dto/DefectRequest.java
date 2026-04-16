package com.toir.defect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DefectRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull UUID equipmentId,
        UUID requestId,
        UUID workOrderId,
        String category,
        String severity,
        String failureReason,
        String rootCause
) {}
