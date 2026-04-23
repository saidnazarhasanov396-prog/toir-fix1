package com.toir.dto.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalRequestDto(
        UUID id,
        String documentType,
        UUID documentId,
        String title,
        UUID requesterId,
        ApprovalStatus status,
        int currentStep,
        Instant completedAt,
        String description,
        Instant createdAt,
        List<ApprovalStepDto> steps
) {
    public static ApprovalRequestDto from(ApprovalRequest r) {
        return new ApprovalRequestDto(
                r.getId(), r.getDocumentType(), r.getDocumentId(), r.getTitle(), r.getRequesterId(),
                r.getStatus(), r.getCurrentStep(), r.getCompletedAt(), r.getDescription(),
                r.getCreatedAt(),
                r.getSteps().stream().map(ApprovalStepDto::from).toList());
    }
}
