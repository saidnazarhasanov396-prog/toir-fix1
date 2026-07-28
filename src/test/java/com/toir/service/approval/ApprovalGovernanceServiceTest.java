package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.EscalationEvent;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.ApprovalHistoryRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.EscalationEventRepository;
import com.toir.service.NotificationService;
import com.toir.service.OperationalIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalGovernanceServiceTest {

    ApprovalRequestRepository requestRepository;
    ApprovalHistoryRepository historyRepository;
    ApprovalSlaPolicyService slaPolicyService;
    NotificationService notificationService;
    OperationalIssueService operationalIssueService;
    EscalationEventRepository escalationEventRepository;
    ApprovalGovernanceService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(ApprovalRequestRepository.class);
        historyRepository = mock(ApprovalHistoryRepository.class);
        slaPolicyService = mock(ApprovalSlaPolicyService.class);
        notificationService = mock(NotificationService.class);
        operationalIssueService = mock(OperationalIssueService.class);
        escalationEventRepository = mock(EscalationEventRepository.class);
        when(slaPolicyService.slaFor(any())).thenReturn(Duration.ofHours(24));
        when(slaPolicyService.escalationSeverity()).thenReturn(NotificationSeverity.WARNING);
        when(slaPolicyService.escalationSeverityFor(any())).thenReturn(NotificationSeverity.WARNING);
        service = new ApprovalGovernanceService(
                requestRepository,
                historyRepository,
                slaPolicyService,
                notificationService,
                operationalIssueService,
                escalationEventRepository
        );
    }

    @Test
    void expireOverdueMarksPendingApprovalExpiredAndWritesHistory() {
        ApprovalRequest approval = approval();
        approval.setExpiresAt(Instant.now().minusSeconds(60));
        when(requestRepository.findExpiredPending(any())).thenReturn(List.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int expired = service.expireOverdue();

        assertThat(expired).isEqualTo(1);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.EXPIRED);
        verify(historyRepository).save(any());
        verify(notificationService).notifyApprovalResult(
                eq(approval.getRequesterId()), any(), any(), eq(NotificationSeverity.WARNING),
                eq(com.toir.enums.NotificationEventType.APPROVAL_EXPIRED),
                eq("WORK_ORDER"), eq(approval.getTargetId()), eq(approval.getId())
        );
        verify(escalationEventRepository, never()).save(any());
    }

    @Test
    void escalationCreatesNotificationOperationalIssueEscalationEventAndHistory() {
        ApprovalRequest approval = approval();
        approval.setCreatedAt(Instant.now().minus(Duration.ofHours(25)));
        when(requestRepository.findPendingWithoutEscalation()).thenReturn(List.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int escalated = service.escalateOverdue();

        assertThat(escalated).isEqualTo(1);
        assertThat(approval.getEscalatedAt()).isNotNull();
        verify(notificationService).notifyUser(eq(approval.getRequesterId()), any(), any(), eq(NotificationSeverity.WARNING), eq(com.toir.enums.NotificationEventType.APPROVAL_SLA_ESCALATED), eq("APPROVAL_REQUEST"), eq(approval.getId().toString()));
        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.APPROVAL_ESCALATION),
                eq(NotificationSeverity.WARNING),
                isNull(),
                isNull(),
                eq("ApprovalRequest"),
                eq(approval.getId()),
                any(),
                any(),
                any()
        );
        ArgumentCaptor<EscalationEvent> captor = ArgumentCaptor.forClass(EscalationEvent.class);
        verify(escalationEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEntityType()).isEqualTo("ApprovalRequest");
        verify(historyRepository).save(any());
    }

    private ApprovalRequest approval() {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setId(UUID.randomUUID());
        approval.setDocumentType("WORK_ORDER");
        approval.setDocumentId(UUID.randomUUID());
        approval.setTargetType(ApprovalTargetType.WORK_ORDER);
        approval.setTargetId(approval.getDocumentId());
        approval.setTitle("Approve work order");
        approval.setRequesterId(UUID.randomUUID());
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);

        ApprovalStep step = new ApprovalStep();
        step.setRequest(approval);
        step.setStepNumber(1);
        step.setApproverId(UUID.randomUUID());
        step.setDecision(ApprovalDecision.PENDING);
        approval.getSteps().add(step);
        return approval;
    }
}
