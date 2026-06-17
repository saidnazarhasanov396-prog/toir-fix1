package com.toir.dto.approval;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;

public record CreateApprovalRequest(
        String documentType,
        UUID documentId,
        @NotBlank String title,
        @NotNull UUID requesterId,
        String description,
        @NotEmpty List<StepInput> steps,
        ApprovalTargetType targetType,
        UUID targetId,
        ApprovalActionType actionType
) {
    public CreateApprovalRequest(String documentType,
                                 UUID documentId,
                                 String title,
                                 UUID requesterId,
                                 String description,
                                 List<StepInput> steps) {
        this(documentType, documentId, title, requesterId, description, steps, null, null, null);
    }

    public record StepInput(
            @NotNull UUID approverId,
            String approverRole
    ) {}
}
