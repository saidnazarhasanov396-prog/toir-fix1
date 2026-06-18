package com.toir.dto.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(value = {
        "documentType",
        "documentId",
        "targetType",
        "targetId",
        "actionType",
        "requesterId"
})
public record UpdateApprovalRequest(
        @NotBlank String title,
        String description,
        @Valid @NotNull @Size(min = 1) List<CreateApprovalRequest.StepInput> steps
) {
}
