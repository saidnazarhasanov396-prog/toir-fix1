package com.toir.service.approval;

import com.toir.dto.notification.NotificationContent;
import com.toir.entity.ApprovalHistory;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.EscalationEvent;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.enums.SlaTriggerType;
import com.toir.repository.ApprovalHistoryRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.EscalationEventRepository;
import com.toir.service.NotificationEntityTypes;
import com.toir.service.NotificationService;
import com.toir.service.OperationalIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalGovernanceService {

    public static final String ENTITY_TYPE = NotificationEntityTypes.APPROVAL_REQUEST;

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final ApprovalSlaPolicyService slaPolicyService;
    private final NotificationService notificationService;
    private final OperationalIssueService operationalIssueService;
    private final EscalationEventRepository escalationEventRepository;

    @Transactional
    public int expireOverdue() {
        Instant now = Instant.now();
        int expired = 0;
        for (ApprovalRequest request : requestRepository.findExpiredPending(now)) {
            expire(request, null, "Approval expired");
            expired++;
        }
        return expired;
    }

    @Transactional
    public ApprovalRequest expire(ApprovalRequest request, UUID changedBy, String comment) {
        if (request.getStatus() != ApprovalStatus.PENDING) {
            return request;
        }
        ApprovalStatus oldStatus = request.getStatus();
        request.setStatus(ApprovalStatus.EXPIRED);
        request.setCompletedAt(Instant.now());
        ApprovalRequest saved = requestRepository.save(request);
        record(saved, oldStatus, ApprovalStatus.EXPIRED, changedBy, comment);
        notificationService.notifyApprovalResult(
                saved.getRequesterId(),
                new NotificationContent(
                        "Срок согласования истёк: " + saved.getTitle(),
                        "Срок запроса на согласование " + saved.getTitle() + " истёк.",
                        "Tasdiqlash muddati tugadi: " + saved.getTitle(),
                        saved.getTitle() + " tasdiqlash so‘rovining muddati tugadi.",
                        "Approval expired: " + saved.getTitle(),
                        "Approval request " + saved.getTitle() + " expired."
                ),
                NotificationSeverity.WARNING,
                NotificationEventType.APPROVAL_EXPIRED,
                saved.getTargetType() == null ? NotificationEntityTypes.APPROVAL_REQUEST : saved.getTargetType().name(),
                saved.getTargetId(),
                saved.getId()
        );
        return saved;
    }

    @Transactional
    public int escalateOverdue() {
        Instant now = Instant.now();
        int escalated = 0;
        for (ApprovalRequest request : requestRepository.findPendingWithoutEscalation()) {
            if (request.getCreatedAt() == null || !request.getCreatedAt().plus(slaPolicyService.slaFor(request)).isBefore(now)) {
                continue;
            }
            escalate(request, "Approval SLA exceeded");
            escalated++;
        }
        return escalated;
    }

    @Transactional
    public ApprovalRequest escalate(ApprovalRequest request, String comment) {
        if (request.getStatus() != ApprovalStatus.PENDING || request.getEscalatedAt() != null) {
            return request;
        }
        request.setEscalatedAt(Instant.now());
        ApprovalRequest saved = requestRepository.save(request);
        notifyEscalation(saved);
        openOperationalIssue(saved);
        raiseEscalationEvent(saved, comment);
        record(saved, ApprovalStatus.PENDING, ApprovalStatus.PENDING, null, comment);
        return saved;
    }

    @Transactional
    public void record(ApprovalRequest request,
                       ApprovalStatus oldStatus,
                       ApprovalStatus newStatus,
                       UUID changedBy,
                       String comment) {
        record(request, oldStatus, newStatus, changedBy, comment, request == null ? null : request.getActionType());
    }

    @Transactional
    public void record(ApprovalRequest request,
                       ApprovalStatus oldStatus,
                       ApprovalStatus newStatus,
                       UUID changedBy,
                       String comment,
                       ApprovalActionType historyActionType) {
        record(request, oldStatus, newStatus, changedBy, null, comment, historyActionType);
    }

    @Transactional
    public void record(ApprovalRequest request,
                       ApprovalStatus oldStatus,
                       ApprovalStatus newStatus,
                       UUID changedBy,
                       UUID delegatedForId,
                       String comment,
                       ApprovalActionType historyActionType) {
        record(request, oldStatus, newStatus, changedBy, delegatedForId, comment, historyActionType, null);
    }

    @Transactional
    public void record(ApprovalRequest request,
                       ApprovalStatus oldStatus,
                       ApprovalStatus newStatus,
                       UUID changedBy,
                       UUID delegatedForId,
                       String comment,
                       ApprovalActionType historyActionType,
                       ApprovalStep decisionStep) {
        if (request == null || request.getId() == null || newStatus == null) {
            return;
        }
        ApprovalHistory history = new ApprovalHistory();
        history.setApprovalId(request.getId());
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setDelegatedForId(delegatedForId);
        history.setComment(comment);
        history.setChangedAt(Instant.now());
        history.setActionType(historyActionType);
        history.setTargetType(request.getTargetType());
        history.setTargetId(request.getTargetId());
        if (decisionStep != null) {
            history.setStepId(decisionStep.getId());
            history.setStepNumber(decisionStep.getStepNumber());
            history.setApprovalRound(decisionStep.getApprovalRound());
            history.setDecision(decisionStep.getDecision());
        }
        historyRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> history(UUID approvalId) {
        return historyRepository.findAllByApprovalIdAndIsDeletedFalseOrderByChangedAtAsc(approvalId);
    }

    private void notifyEscalation(ApprovalRequest request) {
        NotificationSeverity severity = slaPolicyService.escalationSeverityFor(request);
        currentPendingStep(request).ifPresent(step -> notificationService.notifyUser(
                step.getApproverId(),
                new NotificationContent(
                        "Превышен SLA согласования: " + request.getTitle(),
                        "Запрос на согласование " + request.getTitle() + " просрочен.",
                        "Tasdiqlash SLA muddati oshdi: " + request.getTitle(),
                        request.getTitle() + " tasdiqlash so‘rovi kechikdi.",
                        "Approval SLA exceeded: " + request.getTitle(),
                        "Approval request " + request.getTitle() + " is overdue."
                ),
                 severity,
                NotificationEventType.APPROVAL_SLA_ESCALATED,
                ENTITY_TYPE,
                request.getId().toString()
        ));
        notificationService.notifyUser(
                request.getRequesterId(),
                new NotificationContent(
                        "Согласование эскалировано: " + request.getTitle(),
                        "Запрос на согласование " + request.getTitle() + " превысил SLA.",
                        "Tasdiqlash eskalatsiya qilindi: " + request.getTitle(),
                        request.getTitle() + " tasdiqlash so‘rovi SLA muddatidan oshdi.",
                        "Approval escalated: " + request.getTitle(),
                        "Approval request " + request.getTitle() + " exceeded its SLA."
                ),
                 severity,
                NotificationEventType.APPROVAL_SLA_ESCALATED,
                ENTITY_TYPE,
                request.getId().toString()
        );
    }

    private void openOperationalIssue(ApprovalRequest request) {
        operationalIssueService.openOrUpdate(
                OperationalIssueType.APPROVAL_ESCALATION,
                slaPolicyService.escalationSeverityFor(request),
                null,
                null,
                ENTITY_TYPE,
                request.getId(),
                "Approval SLA exceeded: " + request.getTitle(),
                "Approval request " + request.getTitle() + " exceeded its SLA.",
                Map.of(
                        "approvalTitle", request.getTitle() == null ? "" : request.getTitle(),
                        "targetType", request.getTargetType() == null ? "" : request.getTargetType().name(),
                        "targetId", request.getTargetId() == null ? "" : request.getTargetId().toString(),
                        "actionType", request.getActionType() == null ? "" : request.getActionType().name()
                )
        );
    }

    private void raiseEscalationEvent(ApprovalRequest request, String comment) {
        EscalationEvent event = new EscalationEvent();
        event.setEntityType(ENTITY_TYPE);
        event.setEntityId(request.getId().toString());
        event.setTriggerType(SlaTriggerType.APPROVAL_SLA);
        event.setRaisedAt(Instant.now());
        event.setNotes(comment);
        escalationEventRepository.save(event);
    }

    private java.util.Optional<ApprovalStep> currentPendingStep(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> step.getStepNumber() == request.getCurrentStep())
                .findFirst();
    }
}
