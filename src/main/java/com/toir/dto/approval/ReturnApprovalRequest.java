package com.toir.dto.approval;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ReturnApprovalRequest(
        UUID approverId,
        @Min(1) int returnToStep,
        @NotBlank String comment
) {}
