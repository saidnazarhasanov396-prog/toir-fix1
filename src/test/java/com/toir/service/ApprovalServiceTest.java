package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ReturnApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalDelegateRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.approval.ApprovalActionExecutor;
import com.toir.service.approval.ApprovalGovernanceService;
import com.toir.service.approval.ApprovalRouteResolver;
import com.toir.service.approval.ApprovalSlaPolicyService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    ApprovalRequestRepository requestRepository;

    @Mock
    ApprovalDelegateRepository delegateRepository;

    @Mock
    ApprovalActionExecutor approvalActionExecutor;

    @Mock
    ApprovalGovernanceService governanceService;

    @Mock
    ApprovalSlaPolicyService slaPolicyService;

    @Mock
    ApprovalRouteResolver routeResolver;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    ApprovalScopeService approvalScopeService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    NotificationService notificationService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ApprovalService service;

    @Test
    void createOrReuseApprovalForDocumentLocksAndReturnsExistingPendingApproval() {
        UUID workOrderId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest existing = pendingApproval(UUID.randomUUID(), workOrderId, requesterId);

        when(requestRepository.findFirstPendingByTargetAndAction(
                "WORK_ORDER",
                workOrderId,
                ApprovalActionType.APPROVE.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.of(existing));

        ApprovalRequestDto result = service.createOrReuseApprovalForDocument(
                "WORK_ORDER",
                workOrderId,
                ApprovalActionType.APPROVE,
                requesterId,
                approverId,
                "WORK_ORDER_APPROVER",
                "Work order approval",
                "Approval request for work order"
        );

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.documentType()).isEqualTo("WORK_ORDER");
        assertThat(result.documentId()).isEqualTo(workOrderId);
        verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
        );
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
        verify(governanceService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void step2CanReturnToStep1AndReopenSteps() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(approvalId, requesterId, 2, approver1, approver2, UUID.randomUUID());

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestDto result = service.returnToStep(
                approvalId,
                new ReturnApprovalRequest(approver2, 1, "Fix amount")
        );

        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.currentStep()).isEqualTo(1);
        assertThat(result.returned()).isTrue();
        assertThat(result.lastReturnedBy()).isEqualTo(approver2);
        assertThat(result.lastReturnComment()).isEqualTo("Fix amount");
        assertThat(approval.getSteps()).allSatisfy(step -> {
            assertThat(step.getDecision()).isEqualTo(ApprovalDecision.PENDING);
            assertThat(step.getDecidedAt()).isNull();
            assertThat(step.getComment()).isNull();
        });
        verify(governanceService).record(
                eq(approval),
                eq(ApprovalStatus.PENDING),
                eq(ApprovalStatus.PENDING),
                eq(approver2),
                isNull(),
                eq("Returned from step 2 to step 1: Fix amount"),
                eq(ApprovalActionType.RETURNED_TO_STEP)
        );
        verify(notificationService, atLeast(2)).notifyUser(
                any(),
                any(),
                any(),
                any(NotificationSeverity.class),
                any(),
                any()
        );
    }

    @Test
    void step3CanReturnToStep1AndReopenAllAffectedSteps() {
        UUID approvalId = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        UUID approver3 = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId,
                UUID.randomUUID(),
                3,
                approver1,
                approver2,
                approver3
        );

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestDto result = service.returnToStep(
                approvalId,
                new ReturnApprovalRequest(approver3, 1, "Restart review")
        );

        assertThat(result.currentStep()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getSteps()).extracting(ApprovalStep::getDecision)
                .containsExactly(ApprovalDecision.PENDING, ApprovalDecision.PENDING, ApprovalDecision.PENDING);
    }

    @Test
    void cannotReturnToSameOrFutureStep() {
        UUID approvalId = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        UUID approver3 = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId,
                UUID.randomUUID(),
                2,
                approver1,
                approver2,
                approver3
        );

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.returnToStep(approvalId, new ReturnApprovalRequest(approver2, 2, "same")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("returnToStep must be less than currentStep");
        assertThatThrownBy(() -> service.returnToStep(approvalId, new ReturnApprovalRequest(approver2, 3, "future")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("returnToStep must be less than currentStep");
        verify(requestRepository, never()).save(any());
    }

    @Test
    void cannotReturnTerminalApprovals() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approved = pendingMultiStepApproval(approvalId, UUID.randomUUID(), 2, UUID.randomUUID(), approverId);
        approved.setStatus(ApprovalStatus.APPROVED);
        ApprovalRequest rejected = pendingMultiStepApproval(approvalId, UUID.randomUUID(), 2, UUID.randomUUID(), approverId);
        rejected.setStatus(ApprovalStatus.REJECTED);

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId))
                .thenReturn(Optional.of(approved))
                .thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.returnToStep(approvalId, new ReturnApprovalRequest(approverId, 1, "no")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Request is not pending: APPROVED");
        assertThatThrownBy(() -> service.returnToStep(approvalId, new ReturnApprovalRequest(approverId, 1, "no")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Request is not pending: REJECTED");
    }

    @Test
    void cannotReturnExpiredApproval() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(approvalId, UUID.randomUUID(), 2, UUID.randomUUID(), approverId);
        approval.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(governanceService.expire(eq(approval), isNull(), eq("Approval expired before decision")))
                .thenAnswer(invocation -> {
                    approval.setStatus(ApprovalStatus.EXPIRED);
                    return approval;
                });

        assertThatThrownBy(() -> service.returnToStep(approvalId, new ReturnApprovalRequest(approverId, 1, "late")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Request is not pending: EXPIRED");
    }

    @Test
    void cannotReturnExecutedApproval() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(approvalId, UUID.randomUUID(), 2, UUID.randomUUID(), approverId);
        approval.setExecuted(true);

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.returnToStep(approvalId, new ReturnApprovalRequest(approverId, 1, "no")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Executed approvals cannot be returned");
    }

    private ApprovalRequest pendingApproval(UUID id, UUID workOrderId, UUID requesterId) {
        ApprovalRequest approval = new ApprovalRequest();
        ReflectionTestUtils.setField(approval, "id", id);
        ReflectionTestUtils.setField(approval, "createdAt", Instant.now());
        ReflectionTestUtils.setField(approval, "updatedAt", Instant.now());
        approval.setDocumentType("WORK_ORDER");
        approval.setDocumentId(workOrderId);
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.setRequesterId(requesterId);
        approval.setTitle("Work order approval");
        approval.setDescription("Approval request for work order");
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);
        approval.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        return approval;
    }

    private ApprovalRequest pendingMultiStepApproval(UUID id, UUID requesterId, int currentStep, UUID... approverIds) {
        ApprovalRequest approval = pendingApproval(id, UUID.randomUUID(), requesterId);
        approval.setCurrentStep(currentStep);
        approval.getSteps().clear();
        for (int i = 0; i < approverIds.length; i++) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(approval);
            step.setStepNumber(i + 1);
            step.setApproverId(approverIds[i]);
            if (i + 1 < currentStep) {
                step.setDecision(ApprovalDecision.APPROVED);
                step.setDecidedById(approverIds[i]);
                step.setDecidedAt(Instant.now().minus(Duration.ofMinutes(10 - i)));
                step.setComment("Approved step " + (i + 1));
            } else {
                step.setDecision(ApprovalDecision.PENDING);
            }
            approval.getSteps().add(step);
        }
        return approval;
    }
}
