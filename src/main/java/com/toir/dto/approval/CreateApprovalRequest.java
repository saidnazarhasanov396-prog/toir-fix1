package com.toir.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateApprovalRequest(
        @NotBlank String documentType,
        @NotNull UUID documentId,
        @NotBlank String title,
        @NotNull UUID requesterId,
        String description,
        @NotEmpty List<StepInput> steps
) {
    public record StepInput(
            @NotNull UUID approverId,
            String approverRole
    ) {}
}
