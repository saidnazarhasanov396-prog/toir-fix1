package com.toir.dto.approval;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnApprovalRequest(
        @NotNull UUID approverId,
        @Min(1) int returnToStep,
        @NotBlank String comment
) {}
