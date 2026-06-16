package com.toir.dto.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;

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
        List<ApprovalStepDto> steps,
        ApprovalTargetType targetType,
        UUID targetId,
        ApprovalActionType actionType,
        String requesterName,
        String currentApproverName,
        int totalSteps,
        Instant expiresAt,
        boolean escalated,
        boolean overdue,
        String targetDisplayName,
        String targetUrl,
        String resultJson,
        String failureReason,
        boolean canApprove,
        boolean canReject,
        boolean canCancel,
        TargetSummary targetSummary,
        boolean returned,
        Instant lastReturnedAt,
        UUID lastReturnedBy,
        String lastReturnComment
) {
    public ApprovalRequestDto(UUID id,
                              String documentType,
                              UUID documentId,
                              String title,
                              UUID requesterId,
                              ApprovalStatus status,
                              int currentStep,
                              Instant completedAt,
                              String description,
                              Instant createdAt,
                              List<ApprovalStepDto> steps,
                              ApprovalTargetType targetType,
                              UUID targetId,
                              ApprovalActionType actionType,
                              String requesterName,
                              String currentApproverName,
                              int totalSteps,
                              Instant expiresAt,
                              boolean escalated,
                              boolean overdue,
                              String targetDisplayName,
                              String targetUrl,
                              String resultJson,
                              String failureReason,
                              boolean canApprove,
                              boolean canReject,
                              boolean canCancel,
                              TargetSummary targetSummary) {
        this(id, documentType, documentId, title, requesterId, status, currentStep, completedAt, description,
                createdAt, steps, targetType, targetId, actionType, requesterName, currentApproverName, totalSteps,
                expiresAt, escalated, overdue, targetDisplayName, targetUrl, resultJson, failureReason,
                canApprove, canReject, canCancel, targetSummary, false, null, null, null);
    }

    public ApprovalRequestDto(UUID id,
                              String documentType,
                              UUID documentId,
                              String title,
                              UUID requesterId,
                              ApprovalStatus status,
                              int currentStep,
                              Instant completedAt,
                              String description,
                              Instant createdAt,
                              List<ApprovalStepDto> steps) {
        this(id, documentType, documentId, title, requesterId, status, currentStep, completedAt, description,
                createdAt, steps, null, null, null, null, null, steps == null ? 0 : steps.size(), null, false,
                false, title, null, null, null, false, false, false, null, false, null, null, null);
    }

    public static ApprovalRequestDto from(ApprovalRequest r) {
        List<ApprovalStepDto> stepDtos = r.getSteps().stream().map(ApprovalStepDto::from).toList();
        ApprovalTargetType targetType = r.getTargetType();
        UUID targetId = r.getTargetId();
        String documentType = r.getDocumentType();
        UUID documentId = r.getDocumentId();
        String targetTypeName = targetType == null ? documentType : targetType.name();
        UUID effectiveTargetId = targetId == null ? documentId : targetId;
        String targetUrl = targetUrl(targetTypeName, effectiveTargetId);
        return new ApprovalRequestDto(
                r.getId(), documentType, documentId, r.getTitle(), r.getRequesterId(),
                r.getStatus(), r.getCurrentStep(), r.getCompletedAt(), r.getDescription(),
                r.getCreatedAt(),
                stepDtos,
                targetType,
                targetId,
                r.getActionType(),
                null,
                null,
                stepDtos.size(),
                r.getExpiresAt(),
                r.getEscalatedAt() != null,
                r.getStatus() == ApprovalStatus.PENDING
                        && r.getExpiresAt() != null
                        && r.getExpiresAt().isBefore(Instant.now()),
                r.getTitle(),
                targetUrl,
                r.getResultJson(),
                r.getFailureReason(),
                false,
                false,
                false,
                new TargetSummary(effectiveTargetId, targetTypeName, r.getTitle(), null,
                        r.getStatus() == null ? null : r.getStatus().name(), targetUrl),
                r.getLastReturnedAt() != null,
                r.getLastReturnedAt(),
                r.getLastReturnedBy(),
                r.getLastReturnComment());
    }

    private static String targetUrl(String targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return null;
        }
        return switch (targetType) {
            case "WORK_ORDER" -> "/work-orders/" + targetId;
            case "PPR_PLAN" -> "/ppr-plans/" + targetId;
            case "PROCUREMENT_REQUEST" -> "/procurement-requests/" + targetId;
            case "MAINTENANCE_BUDGET", "BUDGET" -> "/budgets/" + targetId;
            case "MAINTENANCE_DUE_EVENT" -> "/maintenance-due-events/" + targetId;
            case "REPAIR_REQUEST" -> "/repair-requests/" + targetId;
            case "MAINTENANCE_REGULATION" -> "/maintenance-regulations/" + targetId;
            case "REGULATION_CHANGE_PROPOSAL" -> "/regulation-change-proposals/" + targetId;
            case "ACTUAL_COST" -> "/actual-costs/" + targetId;
            case "DEFECT_LIST" -> "/defect-lists/" + targetId;
            case "PLANNED_SHUTDOWN" -> "/planned-shutdowns/" + targetId;
            case "REPAIR_CAMPAIGN" -> "/repair-campaigns/" + targetId;
            default -> "/approvals/targets/" + targetType + "/" + targetId;
        };
    }

    public record TargetSummary(
            UUID id,
            String type,
            String title,
            String code,
            String status,
            String route
    ) {
    }
}
