package com.toir.service;

import com.toir.dto.approval.*;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.ApprovalTieBreakPolicy;
import com.toir.enums.UserStatus;
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
import com.toir.service.approval.LifecycleApprovalRoutePolicy;
import com.toir.service.approval.LifecycleApprovalStartPlan;
import com.toir.service.approval.LifecycleRouteResolution;
import com.toir.service.maintanance.MaintenanceRegulationService;
import com.toir.service.maintanance.MaintenanceScheduleApprovalBinding;
import com.toir.service.maintanance.MaintenanceScheduleApprovalBindingService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.UnexpectedRollbackException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    private static final String LIFECYCLE_IDENTITY_SQL = """
            SELECT COALESCE(target_type, document_type) AS target_type,
                   COALESCE(target_id, document_id) AS target_id,
                   COALESCE(action_type, 'APPROVE') AS action_type
            FROM approval_requests
            WHERE id = ? AND is_deleted = false
            """;

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

    LifecycleApprovalRoutePolicy lifecycleApprovalRoutePolicy = new LifecycleApprovalRoutePolicy();

    @Mock
    ObjectProvider<MaintenanceRegulationService> maintenanceRegulationServiceProvider;

    @Mock
    MaintenanceRegulationService maintenanceRegulationService;

    @Mock
    MaintenanceScheduleApprovalBindingService maintenanceScheduleApprovalBindingService;

    @InjectMocks
    ApprovalService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "lifecycleApprovalRoutePolicy", lifecycleApprovalRoutePolicy);
        ReflectionTestUtils.setField(
                service,
                "maintenanceScheduleApprovalBindingService",
                maintenanceScheduleApprovalBindingService);
    }

    @Test
    void requesterCanUpdatePendingApprovalBeforeAnyDecision() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId, requesterId, 1, UUID.randomUUID(), UUID.randomUUID());
        UpdateApprovalRequest update = new UpdateApprovalRequest(
                "Updated title",
                "Updated description",
                List.of(
                        new CreateApprovalRequest.StepInput(UUID.randomUUID(), null),
                        new CreateApprovalRequest.StepInput(UUID.randomUUID(), null)
                )
        );
        stubSuccessfulUpdate(approvalId, approval, requesterId);

        ApprovalRequestDto result = service.update(approvalId, update);

        assertThat(result.title()).isEqualTo("Updated title");
        assertThat(result.description()).isEqualTo("Updated description");
        assertThat(result.currentStep()).isEqualTo(1);
        assertThat(result.steps()).extracting(step -> step.stepNumber()).containsExactly(1, 2);
        verify(approvalScopeService).assertCanUpdateApproval(approval);
        verify(governanceService).record(
                approval,
                ApprovalStatus.PENDING,
                ApprovalStatus.PENDING,
                requesterId,
                "Approval request updated",
                ApprovalActionType.UPDATED
        );
    }

    @Test
    void sequentialRejectionReturnsToPreviousStepWithoutTerminating() {
        UUID approvalId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId, UUID.randomUUID(), 2, first, second, UUID.randomUUID());
        approval.setRejectionPolicy(ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.reject(
                approvalId, new DecisionRequest(second, "Needs another review"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.currentStep()).isEqualTo(1);
        assertThat(approval.getSteps()).extracting(ApprovalStep::getDecision)
                .containsExactly(ApprovalDecision.PENDING, ApprovalDecision.PENDING, ApprovalDecision.PENDING);
        verifyNoInteractions(approvalActionExecutor);
    }

    @Test
    void previousStepPolicyAtFirstStepReturnsToInitiator() {
        UUID approvalId = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(approvalId, requester, 1, approver);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.reject(
                approvalId, new DecisionRequest(approver, "Fix the request"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REWORK);
        assertThat(result.canApprove()).isFalse();
        assertThat(result.canReject()).isFalse();
        verifyNoInteractions(approvalActionExecutor);
    }

    @Test
    void returnToInitiatorPolicyMovesParallelRequestToReworkWithoutCancellingPeers() {
        UUID approvalId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 2, first, second);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.RETURN_TO_INITIATOR);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.rejectStep(
                approvalId, approval.getSteps().getFirst().getId(), new DecisionRequest(first, "Rework"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REWORK);
        assertThat(approval.getSteps()).extracting(ApprovalStep::getDecision)
                .containsExactly(ApprovalDecision.REJECTED, ApprovalDecision.PENDING);
        verifyNoInteractions(approvalActionExecutor);
    }

    @Test
    void majorityWaitsForEveryVoteAndUsesStrictMajority() {
        UUID approvalId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 4, first, second, third);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.MAJORITY);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"approved\":true}");

        ApprovalRequestDto firstVote = service.rejectStep(
                approvalId, approval.getSteps().getFirst().getId(), new DecisionRequest(first, "Risk"));
        ApprovalRequestDto secondVote = service.approveStep(
                approvalId, approval.getSteps().get(1).getId(), new DecisionRequest(second, "ok"));
        ApprovalRequestDto finalVote = service.approveStep(
                approvalId, approval.getSteps().get(2).getId(), new DecisionRequest(third, "ok"));

        assertThat(firstVote.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(secondVote.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(finalVote.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(finalVote.approvedCount()).isEqualTo(2);
        assertThat(finalVote.rejectedCount()).isEqualTo(1);
        verify(approvalActionExecutor, times(1)).execute(approval);
    }

    @Test
    void majorityApprovesExactTieWhenSnapshotSaysApproveOnTie() {
        UUID approvalId = UUID.randomUUID();
        UUID[] approvers = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(6)
                .toArray(UUID[]::new);
        ApprovalRequest approval = parallelApproval(approvalId, 1, approvers);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.MAJORITY);
        approval.setTieBreakPolicy(ApprovalTieBreakPolicy.APPROVE_ON_TIE);
        decideParallelVotes(approval, 3, 2);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"approved\":true}");

        ApprovalRequestDto result = service.rejectStep(
                approvalId,
                approval.getSteps().get(5).getId(),
                new DecisionRequest(approvers[5], "tie vote"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.approvedCount()).isEqualTo(3);
        assertThat(result.rejectedCount()).isEqualTo(3);
        assertThat(result.tieBreakPolicy()).isEqualTo(ApprovalTieBreakPolicy.APPROVE_ON_TIE);
        assertThat(result.tieBreakApplied()).isTrue();
        verify(approvalActionExecutor, times(1)).execute(approval);
    }

    @Test
    void majorityRejectsExactTieWhenSnapshotSaysRejectOnTie() {
        UUID approvalId = UUID.randomUUID();
        UUID[] approvers = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(6)
                .toArray(UUID[]::new);
        ApprovalRequest approval = parallelApproval(approvalId, 1, approvers);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.MAJORITY);
        approval.setTieBreakPolicy(ApprovalTieBreakPolicy.REJECT_ON_TIE);
        decideParallelVotes(approval, 3, 2);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"rejected\":true}");

        ApprovalRequestDto result = service.rejectStep(
                approvalId,
                approval.getSteps().get(5).getId(),
                new DecisionRequest(approvers[5], "tie vote"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.tieBreakPolicy()).isEqualTo(ApprovalTieBreakPolicy.REJECT_ON_TIE);
        assertThat(result.tieBreakApplied()).isTrue();
        verify(approvalActionExecutor, times(1)).execute(approval);
    }

    @Test
    void majorityIgnoresTieBreakWhenStrictMajorityExists() {
        UUID approvalId = UUID.randomUUID();
        UUID[] approvers = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(6)
                .toArray(UUID[]::new);
        ApprovalRequest approval = parallelApproval(approvalId, 1, approvers);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.MAJORITY);
        approval.setTieBreakPolicy(ApprovalTieBreakPolicy.REJECT_ON_TIE);
        decideParallelVotes(approval, 3, 2);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"approved\":true}");

        ApprovalRequestDto result = service.approveStep(
                approvalId,
                approval.getSteps().get(5).getId(),
                new DecisionRequest(approvers[5], "majority vote"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.approvedCount()).isEqualTo(4);
        assertThat(result.rejectedCount()).isEqualTo(2);
        assertThat(result.tieBreakApplied()).isFalse();
        verify(approvalActionExecutor, times(1)).execute(approval);
    }

    @Test
    void requesterResubmitsReworkUsingPersistedParallelSnapshot() {
        UUID approvalId = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(
                approvalId, 2, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        approval.setRequesterId(requester);
        approval.setRejectionPolicy(ApprovalRejectionPolicy.RETURN_TO_INITIATOR);
        approval.setStatus(ApprovalStatus.REWORK);
        approval.getSteps().getFirst().setDecision(ApprovalDecision.REJECTED);
        stubParallelLock(approvalId, approval);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requester);
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.resubmit(approvalId);

        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.approvalRound()).isEqualTo(3);
        assertThat(result.currentStep()).isZero();
        assertThat(result.rejectionPolicy()).isEqualTo(ApprovalRejectionPolicy.RETURN_TO_INITIATOR);
        assertThat(approval.getSteps()).allSatisfy(step -> {
            assertThat(step.getApprovalRound()).isEqualTo(3);
            assertThat(step.getDecision()).isEqualTo(ApprovalDecision.PENDING);
        });
        assertThat(result.allowedActions()).doesNotContain("RESUBMIT");
    }

    @Test
    void onlyPersistedRequesterCanResubmitRework() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId, UUID.randomUUID(), 1, UUID.randomUUID());
        approval.setStatus(ApprovalStatus.REWORK);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.resubmit(approvalId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("requester");
        verify(requestRepository, never()).save(approval);
    }

    @Test
    void parallelAllStaysPendingUntilLastTaskApprovesThenFinalizesOnce() {
        UUID approvalId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 2, first, second);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"approved\":true}");

        ApprovalRequestDto partial = service.approveStep(
                approvalId, approval.getSteps().getFirst().getId(), new DecisionRequest(first, "ok"));
        ApprovalRequestDto completed = service.approveStep(
                approvalId, approval.getSteps().get(1).getId(), new DecisionRequest(second, "ok"));

        assertThat(partial.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(partial.approvedCount()).isEqualTo(1);
        assertThat(completed.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(completed.approvedCount()).isEqualTo(2);
        verify(approvalActionExecutor, times(1)).execute(approval);
    }

    @Test
    void parallelAllFirstRejectCancelsRemainingTasksAndRequiresComment() {
        UUID approvalId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 3, first, second);
        stubParallelLock(approvalId, approval);
        when(requestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"rejected\":true}");

        assertThatThrownBy(() -> service.rejectStep(
                approvalId, approval.getSteps().getFirst().getId(), new DecisionRequest(first, " ")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("comment is required");

        ApprovalRequestDto rejected = service.rejectStep(
                approvalId, approval.getSteps().getFirst().getId(), new DecisionRequest(first, "risk"));

        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(rejected.steps()).extracting(ApprovalStepDto::decision)
                .containsExactly(ApprovalDecision.REJECTED, ApprovalDecision.CANCELLED);
    }

    @Test
    void parallelAllRejectsDecisionOnAnotherUsersTask() {
        UUID approvalId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 1, owner);
        stubParallelLock(approvalId, approval);

        assertThatThrownBy(() -> service.approveStep(
                approvalId, approval.getSteps().getFirst().getId(),
                new DecisionRequest(UUID.randomUUID(), "not mine")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only assigned approver");
    }

    @Test
    void parallelAllDoesNotSupportReturn() {
        UUID approvalId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 1, owner);
        stubParallelLock(approvalId, approval);

        assertThatThrownBy(() -> service.returnToStep(
                approvalId, new ReturnApprovalRequest(owner, 1, "return")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("PARALLEL_APPROVAL_RETURN_NOT_SUPPORTED");
    }

    @Test
    void parallelAllRuntimeSnapshotCannotBeEditedAfterStart() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = parallelApproval(approvalId, 4, UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.update(
                approvalId,
                new UpdateApprovalRequest(
                        "Changed",
                        null,
                        List.of(new CreateApprovalRequest.StepInput(UUID.randomUUID(), null)))))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("cannot be updated after the approval process has started");

        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void updateKeepsRoleOnlyStepUnassigned() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, requesterId, "OLD_ROLE");
        stubSuccessfulUpdate(approvalId, approval, requesterId);

        ApprovalRequestDto result = service.update(approvalId, new UpdateApprovalRequest(
                "Updated",
                null,
                List.of(new CreateApprovalRequest.StepInput(null, " APPROVAL_MANAGER "))
        ));

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().approverId()).isNull();
        assertThat(result.steps().getFirst().approverRole()).isEqualTo("APPROVAL_MANAGER");
    }

    @Test
    void updateFailsAfterAnyStepDecision() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId, UUID.randomUUID(), 2, UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.update(approvalId, validUpdate()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("process has started");
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateFailsForCompletedApproval() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, UUID.randomUUID(), "APPROVER");
        approval.setStatus(ApprovalStatus.APPROVED);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.update(approvalId, validUpdate()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only pending or draft");
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void unauthorizedUserCannotUpdate() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, UUID.randomUUID(), "APPROVER");
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access denied"))
                .when(approvalScopeService).assertCanUpdateApproval(approval);

        assertThatThrownBy(() -> service.update(approvalId, validUpdate()))
                .isInstanceOf(AccessDeniedException.class);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateDoesNotChangeImmutableApprovalFields() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, requesterId, "APPROVER");
        approval.setTargetType(com.toir.enums.ApprovalTargetType.WORK_ORDER);
        approval.setTargetId(targetId);
        approval.setActionType(ApprovalActionType.APPROVE);
        stubSuccessfulUpdate(approvalId, approval, requesterId);

        service.update(approvalId, validUpdate());

        assertThat(approval.getTargetType()).isEqualTo(com.toir.enums.ApprovalTargetType.WORK_ORDER);
        assertThat(approval.getTargetId()).isEqualTo(targetId);
        assertThat(approval.getDocumentType()).isEqualTo("WORK_ORDER");
        assertThat(approval.getDocumentId()).isEqualTo(targetId);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.APPROVE);
        assertThat(approval.getRequesterId()).isEqualTo(requesterId);
    }

    @Test
    void createKeepsRoleOnlyManualStepUnassigned() {
        UUID documentId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        String approverRole = "WORK_ORDER_APPROVER";
        CreateApprovalRequest request = new CreateApprovalRequest(
                "WORK_ORDER",
                documentId,
                "Role based approval",
                requesterId,
                null,
                List.of(new CreateApprovalRequest.StepInput(null, approverRole))
        );

        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(userRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            return Optional.empty();
        });
        when(requestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.create(request);

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().approverId()).isNull();
        assertThat(result.steps().getFirst().approverRole()).isEqualTo(approverRole);
    }

    @Test
    void pprApprovalWithoutConfiguredTemplateExplainsAdministratorOwnedRoute() {
        UUID planId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        MaintenanceScheduleApprovalBinding binding = new MaintenanceScheduleApprovalBinding(
                planId,
                3L,
                "a".repeat(64),
                1,
                "requester-fingerprint");
        CreateApprovalRequest request = new CreateApprovalRequest(
                ApprovalTargetType.PPR_PLAN.name(),
                planId,
                "PPR plan approval",
                requesterId,
                null,
                List.of(),
                ApprovalTargetType.PPR_PLAN,
                planId,
                ApprovalActionType.APPROVE);

        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(maintenanceScheduleApprovalBindingService.resolveForSubmission(
                ApprovalTargetType.PPR_PLAN,
                planId,
                ApprovalActionType.APPROVE,
                requesterId)).thenReturn(binding);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo("PPR_APPROVAL_TEMPLATE_ROUTE_NOT_CONFIGURED");
                    assertThat(exception.getMessage())
                            .isEqualTo("Active PPR_PLAN_APPROVAL template with at least one "
                                    + "configured approver step is required");
                });

        verify(requestRepository, never()).save(any(ApprovalRequest.class));
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void roleOnlyStepCanBeApprovedByAnyActiveUserWithRole() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String approverRole = "WORK_ORDER_APPROVER";
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, requesterId, approverRole);
        User actor = activeUserWithRole(actorId, approverRole);

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(userRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return actorId.equals(userId) ? Optional.of(actor) : Optional.empty();
        });
        when(requestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestDto result = service.approve(approvalId, new com.toir.dto.approval.DecisionRequest(actorId, "ok"));

        ApprovalStep step = approval.getSteps().getFirst();
        assertThat(step.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(step.getDecidedById()).isEqualTo(actorId);
        assertThat(step.getApproverId()).isNull();
        assertThat(step.getApproverRole()).isEqualTo(approverRole);
        assertThat(result.canApprove()).isFalse();
    }

    @Test
    void roleOnlyStepAcceptsRolePrefixedSecurityAuthority() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, UUID.randomUUID(), "USTA");
        User actor = new User();
        actor.setId(actorId);
        actor.setStatus(UserStatus.ACTIVE);

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(requestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actorId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USTA"))
        ));

        try {
            ApprovalRequestDto beforeDecision = service.findById(approvalId);
            assertThat(beforeDecision.canApprove()).isTrue();
            assertThat(beforeDecision.canReject()).isTrue();

            service.approve(approvalId, new com.toir.dto.approval.DecisionRequest(actorId, "ok"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getSteps().getFirst().getDecidedById()).isEqualTo(actorId);
    }

    @Test
    void unrelatedRoleStepExactAuthorityRejectsMissingPersistedUser() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(
                approvalId, UUID.randomUUID(), "WORK_ORDER_APPROVER");
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actorId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("WORK_ORDER_APPROVER"))));

        try {
            ApprovalRequestDto flags = service.findById(approvalId);
            assertThat(flags.canApprove()).isFalse();
            assertThat(flags.canReject()).isFalse();
            assertThatThrownBy(() -> service.approve(
                    approvalId, new DecisionRequest(actorId, "approve")))
                    .isInstanceOfSatisfying(RestException.class,
                            ex -> assertThat(ex.getStatus().value()).isEqualTo(403));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.PENDING);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void unrelatedRoleStepExactAuthorityRejectsInactivePersistedUser() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(
                approvalId, UUID.randomUUID(), "WORK_ORDER_APPROVER");
        User actor = new User();
        actor.setId(actorId);
        actor.setStatus(UserStatus.INACTIVE);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actorId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("WORK_ORDER_APPROVER"))));

        try {
            ApprovalRequestDto flags = service.findById(approvalId);
            assertThat(flags.canApprove()).isFalse();
            assertThat(flags.canReject()).isFalse();
            assertThatThrownBy(() -> service.reject(
                    approvalId, new DecisionRequest(actorId, "reject")))
                    .isInstanceOfSatisfying(RestException.class,
                            ex -> assertThat(ex.getStatus().value()).isEqualTo(403));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.PENDING);
        verify(requestRepository, never()).save(any());
    }


    @Test
    void configuredOneStepSystemAdminRepairCampaignApprovalIsAValidRuntimeRoute() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, requesterId, "SYSTEM_ADMIN");
        approval.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        approval.setTargetId(UUID.randomUUID());
        approval.setActionType(ApprovalActionType.APPROVE);
        User requester = activeUserWithRole(requesterId, "REQUESTER");

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(userRepository.findByIdAndIsDeletedFalse(requesterId)).thenReturn(Optional.of(requester));

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.canApprove()).isFalse();
        assertThat(result.canReject()).isFalse();
        assertThat(result.canCancel()).isFalse();
        assertThat(result.actionable()).isTrue();
        assertThat(result.stale()).isFalse();
        assertThat(result.staleReason()).isNull();
    }

    @Test
    void terminalRepairCampaignApprovalMarksStepThenRequestApprovedBeforeFinalizerWithoutRouteResolution() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, requesterId, "SYSTEM_ADMIN");
        stubLifecycleActor(approvalId, approval, actorId, "SYSTEM_ADMIN");
        when(approvalActionExecutor.execute(approval)).thenAnswer(invocation -> {
            assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.APPROVED);
            assertThat(approval.getSteps().getFirst().getDecidedById()).isEqualTo(actorId);
            assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            return "{\"status\":\"APPROVED\"}";
        });
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.approve(approvalId, new DecisionRequest(actorId, "approve"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.resultJson()).isEqualTo("{\"status\":\"APPROVED\"}");
        verifyNoInteractions(routeResolver);
    }

    @Test
    void terminalLifecycleApprovalIgnoresSoftDeletedNextStep() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, requesterId, "SYSTEM_ADMIN");
        ApprovalStep deletedNext = new ApprovalStep();
        deletedNext.setRequest(approval);
        deletedNext.setStepNumber(2);
        deletedNext.setApproverRole("OBSOLETE_REVIEWER");
        deletedNext.setDecision(ApprovalDecision.PENDING);
        deletedNext.setDeleted(true);
        approval.getSteps().add(deletedNext);
        stubLifecycleActor(approvalId, approval, actorId, "SYSTEM_ADMIN");
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"status\":\"APPROVED\"}");
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.approve(approvalId, new DecisionRequest(actorId, "approve"));

        assertThat(approval.getCurrentStep()).isEqualTo(1);
        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(deletedNext.getDecision()).isEqualTo(ApprovalDecision.PENDING);
        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        verify(approvalActionExecutor).execute(approval);
        verifyNoInteractions(routeResolver);
    }

    @Test
    void lifecycleDomainFinalizerExceptionPropagatesWithoutConvertingRequestToFailed() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, requesterId, "SYSTEM_ADMIN");
        stubLifecycleActor(approvalId, approval, actorId, "SYSTEM_ADMIN");
        when(approvalActionExecutor.execute(approval))
                .thenThrow(new IllegalStateException("repair campaign finalization failed"));

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(actorId, "approve")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("repair campaign finalization failed");

        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getFailureReason()).isNull();
        verify(requestRepository, never()).save(approval);
        verifyNoInteractions(routeResolver);
    }

    @Test
    void lifecycleRequesterCannotApproveAssignedRoleStep() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        String role = "LIFECYCLE_APPROVER";
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, requesterId, role);
        stubLifecycleActor(approvalId, approval, requesterId, role);

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(requesterId, "approve")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleRequesterCannotRejectAssignedRoleStep() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        String role = "LIFECYCLE_APPROVER";
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, requesterId, role);
        stubLifecycleActor(approvalId, approval, requesterId, role);

        assertThatThrownBy(() -> service.reject(approvalId, new DecisionRequest(requesterId, "reject")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleDecisionLocksDomainThenApprovalAndRereadsCancelledRequestBeforeMutation() {
        UUID approvalId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest cancelled = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "LIFECYCLE_APPROVER");
        cancelled.setTargetId(targetId);
        cancelled.setStatus(ApprovalStatus.CANCELLED);
        ApprovalStep persistedStep = cancelled.getSteps().getFirst();
        stubLifecycleMutationLock(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, targetId, cancelled);

        assertThatThrownBy(() -> service.approve(
                approvalId, new DecisionRequest(actorId, "must not apply")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Request is not pending: CANCELLED");

        InOrder lockOrder = org.mockito.Mockito.inOrder(jdbcTemplate, requestRepository);
        lockOrder.verify(jdbcTemplate).query(eq(LIFECYCLE_IDENTITY_SQL), any(RowMapper.class), eq(approvalId));
        lockOrder.verify(jdbcTemplate).query(
                eq(lifecycleDomainLockSql(ApprovalTargetType.REPAIR_CAMPAIGN)),
                any(RowMapper.class), eq(targetId));
        lockOrder.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class));
        lockOrder.verify(requestRepository).findByIdAndIsDeletedFalseForUpdate(approvalId);
        assertThat(cancelled.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(cancelled.getSteps()).containsExactly(persistedStep);
        assertThat(persistedStep.getDecision()).isEqualTo(ApprovalDecision.PENDING);
        assertThat(persistedStep.getDecidedById()).isNull();
        verify(requestRepository, never()).save(any());
        verifyNoInteractions(approvalActionExecutor, governanceService);
    }

    @Test
    void supportedLifecycleDecisionUsesLockedRereadAndSucceedsAfterBothLocks() {
        UUID approvalId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecyclePending(
                approvalId,
                ApprovalTargetType.PLANNED_SHUTDOWN,
                targetId,
                "{}",
                new CreateApprovalRequest.StepInput(actorId, null),
                new CreateApprovalRequest.StepInput(null, "SECOND_REVIEWER"));
        stubLifecycleMutationLock(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, targetId, approval);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.approve(
                approvalId, new DecisionRequest(actorId, "first step approved"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.currentStep()).isEqualTo(2);
        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(approval.getSteps().getFirst().getDecidedById()).isEqualTo(actorId);
        InOrder lockOrder = org.mockito.Mockito.inOrder(jdbcTemplate, requestRepository);
        lockOrder.verify(jdbcTemplate).query(
                eq(LIFECYCLE_IDENTITY_SQL), any(RowMapper.class), eq(approvalId));
        lockOrder.verify(jdbcTemplate).query(
                eq(lifecycleDomainLockSql(ApprovalTargetType.PLANNED_SHUTDOWN)),
                any(RowMapper.class), eq(targetId));
        lockOrder.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
        lockOrder.verify(requestRepository).findByIdAndIsDeletedFalseForUpdate(approvalId);
        lockOrder.verify(requestRepository).save(approval);
        verifyNoInteractions(approvalActionExecutor);
    }

    @Test
    void unrelatedIdentityUsesRowLockWithoutLifecycleDomainLocks() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest workOrder = pendingMultiStepApproval(
                approvalId, requesterId, 1, UUID.randomUUID());
        UUID targetId = workOrder.getTargetId();
        when(jdbcTemplate.query(eq(LIFECYCLE_IDENTITY_SQL), any(RowMapper.class), eq(approvalId)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
                    when(rs.getString("target_type")).thenReturn(ApprovalTargetType.WORK_ORDER.name());
                    when(rs.getObject("target_id", UUID.class)).thenReturn(targetId);
                    when(rs.getString("action_type")).thenReturn(ApprovalActionType.APPROVE.name());
                    return List.of(mapper.mapRow(rs, 0));
                });
        when(requestRepository.findByIdAndIsDeletedFalseForUpdate(approvalId)).thenReturn(Optional.of(workOrder));
        when(requestRepository.save(workOrder)).thenReturn(workOrder);

        ApprovalRequestDto result = service.cancel(approvalId);

        assertThat(result.status()).isEqualTo(ApprovalStatus.CANCELLED);
        verify(requestRepository).findByIdAndIsDeletedFalseForUpdate(approvalId);
        verify(jdbcTemplate, never()).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
        verify(jdbcTemplate, never()).query(
                eq(lifecycleDomainLockSql(ApprovalTargetType.REPAIR_CAMPAIGN)),
                any(RowMapper.class), any(UUID.class));
        verify(jdbcTemplate, never()).query(
                eq(lifecycleDomainLockSql(ApprovalTargetType.PLANNED_SHUTDOWN)),
                any(RowMapper.class), any(UUID.class));
    }

    @Test
    void lifecycleUserCancelUsesDomainAndApprovalLocksBeforeLockedReread() {
        UUID approvalId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "REVIEWER");
        approval.setTargetId(targetId);
        stubLifecycleMutationLock(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, targetId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.cancel(approvalId);

        assertThat(result.status()).isEqualTo(ApprovalStatus.CANCELLED);
        InOrder order = org.mockito.Mockito.inOrder(jdbcTemplate, requestRepository);
        order.verify(jdbcTemplate).query(
                eq(LIFECYCLE_IDENTITY_SQL), any(RowMapper.class), eq(approvalId));
        order.verify(jdbcTemplate).query(
                eq(lifecycleDomainLockSql(ApprovalTargetType.REPAIR_CAMPAIGN)),
                any(RowMapper.class), eq(targetId));
        order.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
        order.verify(requestRepository).findByIdAndIsDeletedFalseForUpdate(approvalId);
        order.verify(requestRepository).save(approval);
        verify(approvalScopeService).assertCanCancelApproval(approval);
    }

    @Test
    void lifecycleReturnToStepUsesDomainAndApprovalLocksBeforeReopeningRoute() {
        UUID approvalId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID firstActor = UUID.randomUUID();
        UUID currentActor = UUID.randomUUID();
        ApprovalRequest approval = lifecycleApprovalWithApprovedFirstStep(
                approvalId,
                ApprovalTargetType.PLANNED_SHUTDOWN,
                UUID.randomUUID(),
                firstActor,
                "CURRENT_REVIEWER");
        approval.setTargetId(targetId);
        User actor = activeUserWithRole(currentActor, "CURRENT_REVIEWER");
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(currentActor);
        when(userRepository.findByIdAndIsDeletedFalse(currentActor)).thenReturn(Optional.of(actor));
        stubLifecycleMutationLock(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, targetId, approval);
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.returnToStep(
                approvalId, new ReturnApprovalRequest(currentActor, 1, "Rework scope evidence"));

        assertThat(result.currentStep()).isEqualTo(1);
        assertThat(approval.getSteps()).extracting(ApprovalStep::getDecision)
                .containsExactly(ApprovalDecision.PENDING, ApprovalDecision.PENDING);
        InOrder order = org.mockito.Mockito.inOrder(jdbcTemplate, requestRepository);
        order.verify(jdbcTemplate).query(
                eq(LIFECYCLE_IDENTITY_SQL), any(RowMapper.class), eq(approvalId));
        order.verify(jdbcTemplate).query(
                eq(lifecycleDomainLockSql(ApprovalTargetType.PLANNED_SHUTDOWN)),
                any(RowMapper.class), eq(targetId));
        order.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
        order.verify(requestRepository).findByIdAndIsDeletedFalseForUpdate(approvalId);
        order.verify(requestRepository).save(approval);
    }

    @Test
    void lifecycleRepeatedApprovedActorCannotApproveCurrentStep() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleApprovalWithApprovedFirstStep(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), actorId, "SECOND_REVIEWER");
        stubLifecycleActor(approvalId, approval, actorId, "SECOND_REVIEWER");

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(actorId, "approve again")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleRepeatedApprovedActorCanRejectWhenEligibleForCurrentStep() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleApprovalWithApprovedFirstStep(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, UUID.randomUUID(), actorId, "SECOND_REVIEWER");
        stubLifecycleActor(approvalId, approval, actorId, "SECOND_REVIEWER");
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.reject(approvalId, new DecisionRequest(actorId, "reject"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getSteps().get(1).getDecision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(approval.getSteps().get(1).getDecidedById()).isEqualTo(actorId);
    }

    @Test
    void lifecycleWrongRoleCannotApproveCurrentStep() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "REQUIRED_ROLE");
        stubLifecycleActor(approvalId, approval, actorId, "OTHER_ROLE");

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(actorId, "approve")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleUnassignedExplicitActorCannotRejectCurrentStep() {
        UUID approvalId = UUID.randomUUID();
        UUID assignedActorId = UUID.randomUUID();
        UUID otherActorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleExplicitApproval(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, UUID.randomUUID(), assignedActorId);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(otherActorId);

        assertThatThrownBy(() -> service.reject(approvalId, new DecisionRequest(otherActorId, "reject")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleRoleDecisionRejectsSuppliedActorThatDoesNotMatchAuthenticatedPrincipal() {
        UUID approvalId = UUID.randomUUID();
        UUID suppliedActorId = UUID.randomUUID();
        UUID authenticatedActorId = UUID.randomUUID();
        String configuredRole = "CONFIGURED_REVIEWER";
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), configuredRole);
        User suppliedActor = activeUserWithRole(suppliedActorId, "OTHER_ROLE");
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(authenticatedActorId);
        org.mockito.Mockito.lenient().when(userRepository.findByIdAndIsDeletedFalse(suppliedActorId))
                .thenReturn(Optional.of(suppliedActor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                authenticatedActorId.toString(),
                null,
                List.of(new SimpleGrantedAuthority(configuredRole))));

        try {
            assertThatThrownBy(() -> service.approve(
                    approvalId, new DecisionRequest(suppliedActorId, "spoof")))
                    .isInstanceOfSatisfying(RestException.class,
                            ex -> assertThat(ex.getStatus().value()).isEqualTo(403));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.PENDING);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleExplicitDecisionRejectsSuppliedActorThatDoesNotMatchAuthenticatedPrincipal() {
        UUID approvalId = UUID.randomUUID();
        UUID assignedActorId = UUID.randomUUID();
        UUID authenticatedActorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleExplicitApproval(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, UUID.randomUUID(), assignedActorId);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(authenticatedActorId);

        assertThatThrownBy(() -> service.reject(
                approvalId, new DecisionRequest(assignedActorId, "spoof")))
                .isInstanceOfSatisfying(RestException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.PENDING);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void lifecycleWildcardOnlyAdminCannotSubstituteForConfiguredRoleInActionFlags() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "CONFIGURED_REVIEWER");
        stubLifecycleActor(approvalId, approval, actorId, "OTHER_ROLE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actorId.toString(), null, List.of(new SimpleGrantedAuthority("*"))));

        try {
            ApprovalRequestDto result = service.findById(approvalId);

            assertThat(result.canApprove()).isFalse();
            assertThat(result.canReject()).isFalse();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void explicitlyConfiguredSystemAdminRoleIsEligibleForLifecycleActionFlags() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, UUID.randomUUID(), "SYSTEM_ADMIN");
        stubLifecycleActor(approvalId, approval, actorId, "OTHER_ROLE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actorId.toString(), null, List.of(new SimpleGrantedAuthority("SYSTEM_ADMIN"))));

        try {
            ApprovalRequestDto result = service.findById(approvalId);

            assertThat(result.canApprove()).isTrue();
            assertThat(result.canReject()).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void lifecycleCancellationPermissionIsComputedSeparatelyFromDecisionEligibility() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "CONFIGURED_REVIEWER");
        stubLifecycleActor(approvalId, approval, actorId, "OTHER_ROLE");

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.canApprove()).isFalse();
        assertThat(result.canReject()).isFalse();
        assertThat(result.canCancel()).isTrue();
    }

    @Test
    void lifecycleCancellationDenialDoesNotDisableEligibleApproveOrReject() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, UUID.randomUUID(), "CONFIGURED_REVIEWER");
        stubLifecycleActor(approvalId, approval, actorId, "CONFIGURED_REVIEWER");
        doThrow(new AccessDeniedException("cancel denied"))
                .when(approvalScopeService).assertCanCancelApproval(approval);

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.canApprove()).isTrue();
        assertThat(result.canReject()).isTrue();
        assertThat(result.canCancel()).isFalse();
    }

    @Test
    void expiredPendingLifecycleRequestDisablesDecisionsWithoutMutatingDuringDtoConversion() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "CONFIGURED_REVIEWER");
        approval.setExpiresAt(Instant.now().minusSeconds(60));
        stubLifecycleActor(approvalId, approval, actorId, "CONFIGURED_REVIEWER");

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.canApprove()).isFalse();
        assertThat(result.canReject()).isFalse();
        assertThat(result.canCancel()).isTrue();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.PENDING);
        verifyNoInteractions(governanceService);
    }

    @Test
    void malformedPendingLifecycleRouteMakesEveryActionFlagFalse() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.REPAIR_CAMPAIGN, UUID.randomUUID(), "SYSTEM_ADMIN");
        approval.getSteps().clear();
        stubLifecycleActor(approvalId, approval, actorId, "SYSTEM_ADMIN");

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.canApprove()).isFalse();
        assertThat(result.canReject()).isFalse();
        assertThat(result.canCancel()).isFalse();
    }

    @Test
    void nonPendingLifecycleRequestMakesEveryActionFlagFalse() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = lifecycleRoleApproval(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, UUID.randomUUID(), "SYSTEM_ADMIN");
        ApprovalStep step = approval.getSteps().getFirst();
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(actorId);
        step.setDecidedAt(Instant.now());
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setCompletedAt(Instant.now());
        stubLifecycleActor(approvalId, approval, actorId, "SYSTEM_ADMIN");

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.canApprove()).isFalse();
        assertThat(result.canReject()).isFalse();
        assertThat(result.canCancel()).isFalse();
    }

    @Test
    void noncanonicalRepairCampaignApprovalCannotBeApprovedDirectly() {
        UUID approvalId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, UUID.randomUUID(), "SYSTEM_ADMIN");
        approval.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        approval.setTargetId(UUID.randomUUID());
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.getSteps().clear();
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(
                approvalId, new com.toir.dto.approval.DecisionRequest(actorId, "approve")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void noncanonicalRepairCampaignApprovalCannotBeCancelledDirectly() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, UUID.randomUUID(), "SYSTEM_ADMIN");
        approval.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        approval.setTargetId(UUID.randomUUID());
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.getSteps().clear();
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.cancel(approvalId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void roleOnlyStepCannotBeApprovedByUserWithoutRole() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String approverRole = "WORK_ORDER_APPROVER";
        ApprovalRequest approval = pendingRoleOnlyApproval(approvalId, requesterId, approverRole);
        User actor = activeUserWithRole(actorId, "OTHER_ROLE");

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(userRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return actorId.equals(userId) ? Optional.of(actor) : Optional.empty();
        });

        assertThatThrownBy(() -> service.approve(approvalId, new com.toir.dto.approval.DecisionRequest(actorId, "no")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only designated approver");
        verify(requestRepository, never()).save(any());
    }

    @Test
    void userSpecificStepStillRejectsUnassignedUser() {
        UUID approvalId = UUID.randomUUID();
        UUID assignedApproverId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(approvalId, UUID.randomUUID(), 1, assignedApproverId);

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(approvalId, new com.toir.dto.approval.DecisionRequest(otherUserId, "spoof")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only designated approver");
        verify(requestRepository, never()).save(any());
    }

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
    void compatiblePendingIsReusedWhenNoActiveTemplateExists() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":4}";
        ApprovalRequest existing = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "PLANNER"),
                new CreateApprovalRequest.StepInput(UUID.randomUUID(), null));
        List<ApprovalStep> originalSteps = List.copyOf(existing.getSteps());

        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(existing));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);
        ApprovalRequestDto result = service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "ignored", "ignored", payload);

        assertThat(plan.reusable()).isTrue();
        assertThat(plan.reusableRequest()).isSameAs(existing);
        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(existing.getSteps()).containsExactlyElementsOf(originalSteps);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getId)
                .containsExactlyElementsOf(originalSteps.stream().map(ApprovalStep::getId).toList());
        verifyNoInteractions(routeResolver);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void compatiblePendingIsReusedWhenMultipleTemplatesExist() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":8}";
        ApprovalRequest existing = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.PLANNED_SHUTDOWN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "OPERATIONS"));
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.PLANNED_SHUTDOWN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(existing));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.PLANNED_SHUTDOWN, targetId, ApprovalActionType.APPROVE, true, payload);

        assertThat(plan.reusableRequest()).isSameAs(existing);
        assertThat(plan.failure()).isEqualTo(LifecycleApprovalRoutePolicy.Reason.VALID);
        verifyNoInteractions(routeResolver);
    }

    @Test
    void editedOrDeactivatedTemplateDoesNotChangePendingSteps() {
        UUID targetId = UUID.randomUUID();
        UUID explicitApprover = UUID.randomUUID();
        String payload = "{\"scopeVersion\":13}";
        ApprovalRequest existing = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "OLD_ROLE"),
                new CreateApprovalRequest.StepInput(explicitApprover, null));
        List<ApprovalStep> originalSteps = List.copyOf(existing.getSteps());
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(existing));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);
        service.materializeLifecycleApproval(plan, UUID.randomUUID(), "ignored", null, payload);

        assertThat(existing.getSteps()).containsExactlyElementsOf(originalSteps);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getStepNumber)
                .containsExactly(1, 2);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getApproverRole)
                .containsExactly("OLD_ROLE", null);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getApproverId)
                .containsExactly(null, explicitApprover);
        verifyNoInteractions(routeResolver);
    }

    @Test
    void newRequestResolvesAndFreezesTemplateExactlyOnce() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID explicitApprover = UUID.randomUUID();
        String payload = "{\"scopeVersion\":21}";
        ArrayList<CreateApprovalRequest.StepInput> resolvedSteps = new ArrayList<>(List.of(
                new CreateApprovalRequest.StepInput(null, " PLANNER "),
                new CreateApprovalRequest.StepInput(explicitApprover, null)));
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of());
        when(routeResolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE))
                .thenReturn(new LifecycleRouteResolution(resolvedSteps, LifecycleApprovalRoutePolicy.Reason.VALID));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, false, null);
        org.mockito.InOrder planningOrder = org.mockito.Mockito.inOrder(
                jdbcTemplate, requestRepository, routeResolver);
        planningOrder.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class));
        planningOrder.verify(requestRepository).findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name());
        planningOrder.verify(routeResolver).resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE);
        verify(requestRepository, never()).save(any(ApprovalRequest.class));
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
        resolvedSteps.clear();
        ApprovalRequestDto result = service.materializeLifecycleApproval(
                plan, requesterId, "Campaign approval", "Approve", payload);

        assertThat(plan.creatable()).isTrue();
        assertThat(plan.frozenSteps()).hasSize(2);
        assertThat(result.targetType()).isEqualTo(ApprovalTargetType.REPAIR_CAMPAIGN);
        assertThat(result.targetId()).isEqualTo(targetId);
        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPayloadJson()).isEqualTo(payload);
        assertThat(saved.getValue().getRequesterId()).isEqualTo(requesterId);
        assertThat(saved.getValue().getSteps()).extracting(ApprovalStep::getApproverRole)
                .containsExactly(" PLANNER ", null);
        assertThat(saved.getValue().getSteps()).extracting(ApprovalStep::getApproverId)
                .containsExactly(null, explicitApprover);
        verify(routeResolver).resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE);
        verifyNoMoreInteractions(routeResolver);
    }

    @Test
    void materializationDoesNotResolveTemplateAgain() {
        UUID targetId = UUID.randomUUID();
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                targetId,
                ApprovalActionType.APPROVE,
                null,
                List.of(new CreateApprovalRequest.StepInput(null, "OPERATIONS")),
                LifecycleApprovalRoutePolicy.Reason.VALID);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Shutdown approval", null, "{\"scopeVersion\":9}");

        verifyNoInteractions(routeResolver);
        verify(requestRepository).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void reusableRequestPreservesEveryPersistedStepIdAndAssignment() {
        UUID targetId = UUID.randomUUID();
        UUID explicitApprover = UUID.randomUUID();
        String payload = "{\"scopeVersion\":55}";
        ApprovalRequest existing = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "ROLE_ONE"),
                new CreateApprovalRequest.StepInput(explicitApprover, null),
                new CreateApprovalRequest.StepInput(null, "ROLE_ONE"));
        List<UUID> stepIds = existing.getSteps().stream().map(ApprovalStep::getId).toList();
        List<UUID> approverIds = existing.getSteps().stream().map(ApprovalStep::getApproverId).toList();
        List<String> roles = existing.getSteps().stream().map(ApprovalStep::getApproverRole).toList();
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(existing));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);
        service.materializeLifecycleApproval(plan, UUID.randomUUID(), "ignored", null, payload);

        assertThat(existing.getSteps()).extracting(ApprovalStep::getId).containsExactlyElementsOf(stepIds);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getApproverId).containsExactlyElementsOf(approverIds);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getApproverRole).containsExactlyElementsOf(roles);
        assertThat(existing.getSteps()).extracting(ApprovalStep::getStepNumber).containsExactly(1, 2, 3);
        verify(requestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(routeResolver);
    }

    @Test
    void newMaterializationPreservesRoleAndExplicitAssignments() {
        UUID targetId = UUID.randomUUID();
        UUID explicitApprover = UUID.randomUUID();
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                ApprovalActionType.APPROVE,
                null,
                List.of(
                        new CreateApprovalRequest.StepInput(null, " MECHANICAL_REVIEW "),
                        new CreateApprovalRequest.StepInput(explicitApprover, null)),
                LifecycleApprovalRoutePolicy.Reason.VALID);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Campaign approval", null, "{\"scopeVersion\":89}");

        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSteps()).extracting(ApprovalStep::getApproverRole)
                .containsExactly(" MECHANICAL_REVIEW ", null);
        assertThat(saved.getValue().getSteps()).extracting(ApprovalStep::getApproverId)
                .containsExactly(null, explicitApprover);
    }

    @Test
    void parallelMaterializationPersistsOnlyExplicitPersonalSteps() {
        UUID targetId = UUID.randomUUID();
        UUID firstApprover = UUID.randomUUID();
        UUID secondApprover = UUID.randomUUID();
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                ApprovalActionType.APPROVE,
                null,
                List.of(
                        new CreateApprovalRequest.StepInput(firstApprover, null),
                        new CreateApprovalRequest.StepInput(secondApprover, null)),
                LifecycleApprovalRoutePolicy.Reason.VALID,
                ApprovalFlowType.PARALLEL_ALL,
                UUID.randomUUID(),
                4L);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Campaign approval", null, "{\"scopeVersion\":89}");

        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSteps()).allSatisfy(step -> {
            assertThat(step.getApproverId()).isNotNull();
            assertThat(step.getApproverRole()).isNull();
            assertThat(step.getFlowType()).isEqualTo(ApprovalFlowType.PARALLEL_ALL);
        });
    }

    @Test
    void lifecycleMaterializationSnapshotsRejectionPolicy() {
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                UUID.randomUUID(),
                ApprovalActionType.APPROVE,
                null,
                List.of(new CreateApprovalRequest.StepInput(null, "MANAGER")),
                LifecycleApprovalRoutePolicy.Reason.VALID,
                ApprovalFlowType.SEQUENTIAL,
                ApprovalRejectionPolicy.RETURN_TO_INITIATOR,
                UUID.randomUUID(),
                5L);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Campaign approval", null, "{}");

        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRejectionPolicy())
                .isEqualTo(ApprovalRejectionPolicy.RETURN_TO_INITIATOR);
    }

    @ParameterizedTest
    @EnumSource(
            value = ApprovalTargetType.class,
            names = {"REPAIR_CAMPAIGN", "PLANNED_SHUTDOWN"}
    )
    void parallelLifecycleMaterializationRejectsRequesterFromExpandedMixedRoute(
            ApprovalTargetType targetType
    ) {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID explicitApprover = UUID.randomUUID();
        when(requestRepository.findAllPendingByTargetAndAction(
                targetType.name(), targetId, ApprovalActionType.APPROVE.name(),
                ApprovalStatus.PENDING.name()))
                .thenReturn(List.of());
        when(routeResolver.resolveLifecycleRoute(targetType, ApprovalActionType.APPROVE))
                .thenReturn(new LifecycleRouteResolution(
                        List.of(
                                new CreateApprovalRequest.StepInput(requesterId, null),
                                new CreateApprovalRequest.StepInput(explicitApprover, null)),
                        LifecycleApprovalRoutePolicy.Reason.VALID,
                        ApprovalFlowType.PARALLEL_ALL,
                        UUID.randomUUID(),
                        4L));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                targetType, targetId, ApprovalActionType.APPROVE, false, null);

        assertThatThrownBy(() -> service.materializeLifecycleApproval(
                plan, requesterId, "Lifecycle approval", null, "{}"))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getMessage())
                            .isEqualTo("LIFECYCLE_APPROVAL_REQUESTER_ASSIGNEE_CONFLICT");
                });
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedTargetBehaviorIsUnchanged() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest existing = pendingApproval(UUID.randomUUID(), targetId, requesterId);
        when(requestRepository.findFirstPendingByTargetAndAction(
                ApprovalTargetType.WORK_ORDER.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(Optional.of(existing));

        ApprovalRequestDto result = service.createOrReuseApprovalForDocument(
                ApprovalTargetType.WORK_ORDER.name(), targetId, ApprovalActionType.APPROVE,
                requesterId, UUID.randomUUID(), null, "Work order approval", null);

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(requestRepository).findFirstPendingByTargetAndAction(
                ApprovalTargetType.WORK_ORDER.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name());
        verify(requestRepository, never()).findAllPendingByTargetAndAction(any(), any(), any(), any());
        verifyNoInteractions(routeResolver);
    }

    @Test
    void startPlanDefensivelyCopiesFrozenSteps() {
        ArrayList<CreateApprovalRequest.StepInput> supplied = new ArrayList<>(List.of(
                new CreateApprovalRequest.StepInput(null, "FIRST"),
                new CreateApprovalRequest.StepInput(UUID.randomUUID(), null)));

        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                UUID.randomUUID(),
                ApprovalActionType.APPROVE,
                null,
                supplied,
                LifecycleApprovalRoutePolicy.Reason.VALID);
        supplied.clear();

        assertThat(plan.frozenSteps()).hasSize(2);
        assertThatThrownBy(() -> plan.frozenSteps().add(
                new CreateApprovalRequest.StepInput(null, "THIRD")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pendingRequestInReadinessDomainStateIsConflict() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":3}";
        ApprovalRequest existing = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.PLANNED_SHUTDOWN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "OPERATIONS"));
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.PLANNED_SHUTDOWN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(existing));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.PLANNED_SHUTDOWN, targetId, ApprovalActionType.APPROVE, false, null);

        assertThat(plan.failure()).isEqualTo(LifecycleApprovalRoutePolicy.Reason.REQUEST_STATUS_INCONSISTENT);
        assertThatThrownBy(() -> service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Shutdown approval", null, payload))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REQUEST_STATUS_INCONSISTENT");
        assertThat(existing.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        verifyNoInteractions(routeResolver);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void malformedPendingRuntimeRouteIsRouteStaleNotTemplateMismatch() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":5}";
        ApprovalRequest malformed = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "PLANNER"));
        malformed.getSteps().clear();
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(malformed));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);

        assertThat(plan.failure()).isEqualTo(LifecycleApprovalRoutePolicy.Reason.EMPTY_ROUTE);
        assertThat(plan.failure()).isNotIn(
                LifecycleApprovalRoutePolicy.Reason.NO_ACTIVE_TEMPLATE,
                LifecycleApprovalRoutePolicy.Reason.MULTIPLE_ACTIVE_TEMPLATES);
        assertThatThrownBy(() -> service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Campaign approval", null, payload))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("EMPTY_ROUTE")
                .hasMessageNotContaining("REPAIR_CAMPAIGN_")
                .hasMessageNotContaining("PLANNED_SHUTDOWN_");
        assertThat(malformed.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        verifyNoInteractions(routeResolver);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void plannedShutdownMalformedPendingKeepsNeutralFailureBoundary() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":5}";
        ApprovalRequest malformed = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.PLANNED_SHUTDOWN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "OPERATIONS"));
        malformed.getSteps().clear();
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.PLANNED_SHUTDOWN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(malformed));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.PLANNED_SHUTDOWN, targetId, ApprovalActionType.APPROVE, true, payload);

        assertThat(plan.failure()).isEqualTo(LifecycleApprovalRoutePolicy.Reason.EMPTY_ROUTE);
        assertThatThrownBy(() -> service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Shutdown approval", null, payload))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("EMPTY_ROUTE")
                .hasMessageNotContaining("REPAIR_CAMPAIGN_")
                .hasMessageNotContaining("PLANNED_SHUTDOWN_");
        verifyNoInteractions(routeResolver);
    }

    @Test
    void malformedTemplateFailureIsNotMappedAsRuntimeRouteStale() {
        UUID targetId = UUID.randomUUID();
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of());
        when(routeResolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE))
                .thenReturn(new LifecycleRouteResolution(
                        List.of(), LifecycleApprovalRoutePolicy.Reason.EMPTY_ROUTE));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, false, null);

        assertThat(plan.failure()).isEqualTo(LifecycleApprovalRoutePolicy.Reason.EMPTY_ROUTE);
        assertThatThrownBy(() -> service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Campaign approval", null, "{}"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("EMPTY_ROUTE")
                .hasMessageNotContaining("REPAIR_CAMPAIGN_")
                .hasMessageNotContaining("PLANNED_SHUTDOWN_");
    }

    @Test
    void duplicatePendingRequestsAreCancelledExceptOneCompatibleNewest() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":34}";
        ApprovalRequest newest = lifecyclePending(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "NEWEST_ROLE"));
        ApprovalRequest olderCompatible = lifecyclePending(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "OLDER_ROLE"));
        ApprovalRequest scopeStale = lifecyclePending(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, "{\"scopeVersion\":33}",
                new CreateApprovalRequest.StepInput(null, "STALE_ROLE"));
        ReflectionTestUtils.setField(newest, "createdAt", Instant.parse("2026-07-17T12:00:00Z"));
        ReflectionTestUtils.setField(olderCompatible, "createdAt", Instant.parse("2026-07-17T11:00:00Z"));
        ReflectionTestUtils.setField(scopeStale, "createdAt", Instant.parse("2026-07-17T10:00:00Z"));
        ApprovalStep olderStep = olderCompatible.getSteps().getFirst();
        ApprovalStep staleStep = scopeStale.getSteps().getFirst();
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(newest, olderCompatible, scopeStale));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);

        assertThat(plan.reusableRequest()).isSameAs(newest);
        assertThat(newest.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(olderCompatible.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(scopeStale.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(olderCompatible.getSteps().getFirst()).isSameAs(olderStep);
        assertThat(scopeStale.getSteps().getFirst()).isSameAs(staleStep);
        verify(requestRepository).saveAndFlush(olderCompatible);
        verify(requestRepository).saveAndFlush(scopeStale);
        verify(governanceService).record(eq(olderCompatible), eq(ApprovalStatus.PENDING),
                eq(ApprovalStatus.CANCELLED), eq(actorId), eq("DUPLICATE_PENDING_APPROVAL"));
        verify(governanceService).record(eq(scopeStale), eq(ApprovalStatus.PENDING),
                eq(ApprovalStatus.CANCELLED), eq(actorId), eq("Superseded by a newer repair campaign scope snapshot"));
        verifyNoInteractions(routeResolver);
    }

    @Test
    void cancelPendingLifecycleApprovalCancelsEveryExactPendingAndPreservesRuntimeSteps() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String reason = "PLANNED_SHUTDOWN_APPROVAL_SCOPE_INVALIDATED";
        ApprovalRequest pending = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.PLANNED_SHUTDOWN, targetId,
                "{\"scopeVersion\":7}",
                new CreateApprovalRequest.StepInput(null, "OPERATIONS_REVIEWER"),
                new CreateApprovalRequest.StepInput(UUID.randomUUID(), null));
        ApprovalRequest duplicate = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.PLANNED_SHUTDOWN, targetId,
                "{\"scopeVersion\":7}",
                new CreateApprovalRequest.StepInput(null, "SECOND_RUNTIME_ROUTE"));
        List<ApprovalStep> persistedSteps = List.copyOf(pending.getSteps());
        List<ApprovalStep> duplicateSteps = List.copyOf(duplicate.getSteps());
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.PLANNED_SHUTDOWN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(pending, duplicate));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelPendingLifecycleApproval(ApprovalTargetType.PLANNED_SHUTDOWN, targetId, reason);

        assertThat(pending.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(pending.getCompletedAt()).isNotNull();
        assertThat(pending.getFailureReason()).isEqualTo(reason);
        assertThat(pending.getSteps()).containsExactlyElementsOf(persistedSteps);
        assertThat(pending.getSteps()).allMatch(step -> step.getDecision() == ApprovalDecision.PENDING);
        assertThat(duplicate.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(duplicate.getCompletedAt()).isNotNull();
        assertThat(duplicate.getFailureReason()).isEqualTo(reason);
        assertThat(duplicate.getSteps()).containsExactlyElementsOf(duplicateSteps);
        assertThat(duplicate.getSteps()).allMatch(step -> step.getDecision() == ApprovalDecision.PENDING);
        verify(requestRepository).saveAndFlush(pending);
        verify(requestRepository).saveAndFlush(duplicate);
        verify(governanceService).record(
                pending, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED, actorId, reason);
        verify(governanceService).record(
                duplicate, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED, actorId, reason);
        verifyNoInteractions(routeResolver);
    }

    @Test
    void cancelPendingLifecycleApprovalRejectsUnrelatedTargetsWithoutMutation() {
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> service.cancelPendingLifecycleApproval(
                ApprovalTargetType.WORK_ORDER, targetId, "scope changed"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Unsupported lifecycle approval target/action");

        verifyNoInteractions(requestRepository, governanceService, routeResolver);
    }

    @Test
    void explicitRequestAfterScopeCancellationResolvesAndFreezesThenCurrentTemplate() {
        UUID targetId = UUID.randomUUID();
        ApprovalRequest superseded = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId,
                "{\"scopeVersion\":12}",
                new CreateApprovalRequest.StepInput(null, "OLD_TEMPLATE_ROLE"));
        UUID currentExplicitApprover = UUID.randomUUID();
        ArrayList<CreateApprovalRequest.StepInput> thenCurrentRoute = new ArrayList<>(List.of(
                new CreateApprovalRequest.StepInput(null, "NEW_TEMPLATE_ROLE"),
                new CreateApprovalRequest.StepInput(currentExplicitApprover, null)));
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(superseded), List.of());
        when(requestRepository.saveAndFlush(superseded)).thenReturn(superseded);
        when(routeResolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE))
                .thenReturn(new LifecycleRouteResolution(
                        thenCurrentRoute, LifecycleApprovalRoutePolicy.Reason.VALID));

        service.cancelPendingLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                "REPAIR_CAMPAIGN_APPROVAL_SCOPE_INVALIDATED");
        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                ApprovalActionType.APPROVE,
                false,
                null);
        thenCurrentRoute.clear();

        assertThat(superseded.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(plan.creatable()).isTrue();
        assertThat(plan.frozenSteps()).extracting(CreateApprovalRequest.StepInput::approverRole)
                .containsExactly("NEW_TEMPLATE_ROLE", null);
        assertThat(plan.frozenSteps()).extracting(CreateApprovalRequest.StepInput::approverId)
                .containsExactly(null, currentExplicitApprover);
        verify(routeResolver).resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE);
    }

    @Test
    void equalCreatedAtHighBitUuidUsesRepositoryOrderForNewestCompatibleSelection() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":144}";
        ApprovalRequest repositoryFirst = lifecyclePending(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "FIRST"));
        ApprovalRequest repositorySecond = lifecyclePending(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "SECOND"));
        Instant sameCreatedAt = Instant.parse("2026-07-17T12:00:00Z");
        ReflectionTestUtils.setField(repositoryFirst, "createdAt", sameCreatedAt);
        ReflectionTestUtils.setField(repositorySecond, "createdAt", sameCreatedAt);
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(repositoryFirst, repositorySecond));
        when(requestRepository.saveAndFlush(repositorySecond)).thenReturn(repositorySecond);

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);

        assertThat(plan.reusableRequest()).isSameAs(repositoryFirst);
        assertThat(repositoryFirst.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(repositorySecond.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
    }

    @Test
    void legacyAliasPendingIsReusedWithoutMutatingHistoricalAliases() {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":233}";
        ApprovalRequest legacy = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId, payload,
                new CreateApprovalRequest.StepInput(null, "LEGACY_ROLE"));
        ReflectionTestUtils.setField(legacy, "targetType", null);
        ReflectionTestUtils.setField(legacy, "targetId", null);
        legacy.setActionType(null);
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(legacy));

        LifecycleApprovalStartPlan plan = service.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, true, payload);

        assertThat(plan.reusableRequest()).isSameAs(legacy);
        assertThat(legacy.getTargetType()).isNull();
        assertThat(legacy.getTargetId()).isNull();
        assertThat(legacy.getActionType()).isNull();
        assertThat(legacy.getDocumentType()).isEqualTo(ApprovalTargetType.REPAIR_CAMPAIGN.name());
        assertThat(legacy.getDocumentId()).isEqualTo(targetId);
        verifyNoInteractions(routeResolver);
    }

    @Test
    void legacyAliasMalformedRuntimeIsStaleWithoutMutatingHistoricalAliases() {
        UUID approvalId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequest legacy = lifecyclePending(
                approvalId, ApprovalTargetType.PLANNED_SHUTDOWN, targetId, "{}",
                new CreateApprovalRequest.StepInput(null, "LEGACY_ROLE"));
        legacy.getSteps().clear();
        ReflectionTestUtils.setField(legacy, "targetType", null);
        ReflectionTestUtils.setField(legacy, "targetId", null);
        legacy.setActionType(null);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(legacy));

        ApprovalRequestDto result = service.findById(approvalId);

        assertThat(result.stale()).isTrue();
        assertThat(legacy.getTargetType()).isNull();
        assertThat(legacy.getTargetId()).isNull();
        assertThat(legacy.getActionType()).isNull();
    }

    @Test
    void manualCreatablePlanWithNullActionPersistsApprove() {
        UUID targetId = UUID.randomUUID();
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                targetId,
                null,
                null,
                List.of(new CreateApprovalRequest.StepInput(null, " EXACT_ROLE ")),
                LifecycleApprovalRoutePolicy.Reason.VALID);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "Shutdown approval", null, "{}");

        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getActionType()).isEqualTo(ApprovalActionType.APPROVE);
        assertThat(saved.getValue().getSteps().getFirst().getApproverRole()).isEqualTo(" EXACT_ROLE ");
    }

    @Test
    void manualReusablePlanWithNullActionAcceptsLegacyApproveRequest() {
        UUID targetId = UUID.randomUUID();
        ApprovalRequest existing = lifecyclePending(
                UUID.randomUUID(), ApprovalTargetType.REPAIR_CAMPAIGN, targetId, "{}",
                new CreateApprovalRequest.StepInput(null, "ROLE"));
        existing.setActionType(null);
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                null,
                existing,
                List.of(),
                LifecycleApprovalRoutePolicy.Reason.VALID);

        ApprovalRequestDto result = service.materializeLifecycleApproval(
                plan, UUID.randomUUID(), "ignored", null, "{}");

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(existing.getActionType()).isNull();
        verify(requestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(routeResolver);
    }

    @Test
    void requestApprovalValidatesMaintenanceRegulationBeforeCreatingApproval() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        when(maintenanceRegulationServiceProvider.getObject()).thenReturn(maintenanceRegulationService);
        stubTargetMetadata(ApprovalTargetType.MAINTENANCE_REGULATION, "MR-001", "Oil change regulation");
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class)))
                .thenReturn(List.of(new CreateApprovalRequest.StepInput(null, "MAINTENANCE_MANAGER")));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.MAINTENANCE_REGULATION,
                targetId,
                ApprovalActionType.APPROVE,
                "Approve regulation"
        ));

        assertThat(result.targetType()).isEqualTo(ApprovalTargetType.MAINTENANCE_REGULATION);
        assertThat(result.targetId()).isEqualTo(targetId);
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().approverRole()).isEqualTo("MAINTENANCE_MANAGER");
        verify(maintenanceRegulationService).validateCanApprove(targetId);

        verify(approvalScopeService).assertCanCreateApproval(any(CreateApprovalRequest.class));
    }
    @Test
    void repairCampaignRequestFailsWithRouteNotConfiguredWithoutPersistingFallback() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.REPAIR_CAMPAIGN, "RCMP-2026-003", "Configured route required");
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 0L, 0L, "a".repeat(64), 2L);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                ApprovalActionType.APPROVE,
                "submit"
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED");

        verify(requestRepository, never()).save(any(ApprovalRequest.class));
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void repairCampaignCreateCannotBypassMissingConfigurationWithCallerSuppliedSteps() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 0L, 0L, "a".repeat(64), 2L);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(List.of());
        List<CreateApprovalRequest.StepInput> callerSteps = lifecycleRoleInputs(
                "CONFIGURED_REVIEWER_ALPHA", "CONFIGURED_REVIEWER_BETA");

        assertThatThrownBy(() -> service.create(new CreateApprovalRequest(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(),
                targetId,
                "Repair campaign approval",
                requesterId,
                "submit",
                callerSteps,
                ApprovalTargetType.REPAIR_CAMPAIGN,
                targetId,
                ApprovalActionType.APPROVE
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED");

        verify(requestRepository, never()).save(any(ApprovalRequest.class));
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void repairCampaignRequestPreservesVariableConfiguredRuntimeSteps() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.REPAIR_CAMPAIGN, "RCMP-2026-004", "Variable route");
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 0L, 0L, "c".repeat(64), 2L);
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        List<CreateApprovalRequest.StepInput> configuredRoute = lifecycleRoleInputs(
                "CONFIGURED_REVIEWER_ALPHA",
                "CONFIGURED_REVIEWER_BETA",
                "CONFIGURED_REVIEWER_GAMMA");
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(configuredRoute);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, "submit"));

        assertThat(result.steps()).hasSize(configuredRoute.size());
        assertThat(result.steps()).extracting(ApprovalStepDto::approverRole)
                .containsExactly("CONFIGURED_REVIEWER_ALPHA", "CONFIGURED_REVIEWER_BETA",
                        "CONFIGURED_REVIEWER_GAMMA");
        assertThat(result.actionable()).isTrue();
    }

    @Test
    void concurrentRepairCampaignRequestsLockBeforeLookupAndReusePersistedVariablePendingApproval() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":0,\"scopeHash\":\"" + "d".repeat(64)
                + "\",\"campaignVersion\":2}";
        ApprovalRequest existing = repairCampaignPending(
                UUID.randomUUID(), targetId, requesterId, payload,
                List.of("RUNTIME_REVIEWER_ALPHA", "RUNTIME_REVIEWER_BETA"));
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 0L, 0L, "d".repeat(64), 2L);
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(existing));

        ApprovalRequestDto result = service.createOrReuseApprovalForDocument(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId, ApprovalActionType.APPROVE,
                requesterId, null, null, "Repair campaign approval", "submit");

        assertThat(result.id()).isEqualTo(existing.getId());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(jdbcTemplate, requestRepository);
        order.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class));
        order.verify(requestRepository).findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name());
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void repairCampaignRetryCancelsAllMalformedPendingApprovalsAndLeavesOnePending() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        String payload = "{\"scopeVersion\":0,\"scopeHash\":\"" + "e".repeat(64)
                + "\",\"campaignVersion\":2}";
        ApprovalRequest first = repairCampaignPending(
                UUID.randomUUID(), targetId, requesterId, payload, List.of());
        ApprovalRequest second = repairCampaignPending(
                UUID.randomUUID(), targetId, requesterId, payload, List.of());
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.REPAIR_CAMPAIGN, "RCMP-2026-005", "Legacy duplicates");
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 0L, 0L, "e".repeat(64), 2L);
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(first, second));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        List<CreateApprovalRequest.StepInput> configuredRoute = lifecycleRoleInputs(
                "CURRENT_REVIEWER_ALPHA", "CURRENT_REVIEWER_BETA", "CURRENT_REVIEWER_GAMMA");
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(configuredRoute);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            if (saved.getCreatedAt() == null) ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            if (saved.getUpdatedAt() == null) ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, "repair"));

        assertThat(first.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(first.getFailureReason()).isEqualTo("NONCANONICAL_REPAIR_CAMPAIGN_ROUTE");
        assertThat(second.getFailureReason()).isEqualTo("NONCANONICAL_REPAIR_CAMPAIGN_ROUTE");
        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.steps()).hasSize(configuredRoute.size());
        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository, times(3)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).filteredOn(value -> value.getStatus() == ApprovalStatus.PENDING)
                .hasSize(1);
    }

    @Test
    void plannedShutdownApprovalCapturesCurrentScopeVersionWithVariableRuntimeRoute() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.PLANNED_SHUTDOWN, null, "Annual shutdown");
        when(jdbcTemplate.queryForObject(
                "select status from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId)).thenReturn("PENDING_APPROVAL");
        when(jdbcTemplate.queryForObject(
                "select scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId)).thenReturn(8L);
        when(jdbcTemplate.queryForObject(
                "select approval_scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId)).thenReturn(8L);
        when(jdbcTemplate.queryForObject(
                "select approval_scope_hash from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId)).thenReturn("a".repeat(64));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(lifecycleRoleInputs(
                "SHUTDOWN_REVIEWER_ALPHA", "SHUTDOWN_REVIEWER_BETA", "SHUTDOWN_REVIEWER_GAMMA"));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.requestApproval(new ApprovalStartRequest(ApprovalTargetType.PLANNED_SHUTDOWN, targetId,
                ApprovalActionType.APPROVE, "Current scope"));

        ArgumentCaptor<ApprovalRequest> request = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(request.capture());
        assertThat(request.getValue().getPayloadJson()).isEqualTo(
                "{\"scopeVersion\":8,\"scopeHash\":\"" + "a".repeat(64) + "\"}");
        assertThat(request.getValue().getSteps()).extracting(ApprovalStep::getApproverRole)
                .containsExactly("SHUTDOWN_REVIEWER_ALPHA", "SHUTDOWN_REVIEWER_BETA",
                        "SHUTDOWN_REVIEWER_GAMMA");
    }

    @Test
    void plannedShutdownApprovalCreationRejectsNonPendingOrIncompleteSnapshot() {
        UUID targetId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(UUID.randomUUID());
        stubTargetMetadata(ApprovalTargetType.PLANNED_SHUTDOWN, null, "Annual shutdown");
        when(jdbcTemplate.queryForObject(
                "select status from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId)).thenReturn("READINESS_CHECK");

        assertThatThrownBy(() -> service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.PLANNED_SHUTDOWN, targetId, ApprovalActionType.APPROVE, "submit")))
                .hasMessageContaining("PLANNED_SHUTDOWN_NOT_PENDING_APPROVAL");

        when(jdbcTemplate.queryForObject(
                "select status from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId)).thenReturn("PENDING_APPROVAL");
        when(jdbcTemplate.queryForObject(
                "select scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId)).thenReturn(4L);
        when(jdbcTemplate.queryForObject(
                "select approval_scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId)).thenReturn(3L);
        assertThatThrownBy(() -> service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.PLANNED_SHUTDOWN, targetId, ApprovalActionType.APPROVE, "submit")))
                .hasMessageContaining("PLANNED_SHUTDOWN_APPROVAL_SCOPE_STALE");
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void plannedShutdownRetryCancelsStalePendingRequestAndCreatesCurrentSnapshot() {
        UUID targetId = UUID.randomUUID(); UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.PLANNED_SHUTDOWN, null, "Annual shutdown");
        when(jdbcTemplate.queryForObject(
                "select status from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId)).thenReturn("PENDING_APPROVAL");
        when(jdbcTemplate.queryForObject(
                "select scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId)).thenReturn(9L);
        when(jdbcTemplate.queryForObject(
                "select approval_scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId)).thenReturn(9L);
        when(jdbcTemplate.queryForObject(
                "select approval_scope_hash from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId)).thenReturn("b".repeat(64));
        ApprovalRequest stale = new ApprovalRequest(); stale.setId(UUID.randomUUID());
        stale.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN); stale.setTargetId(targetId);
        stale.setActionType(ApprovalActionType.APPROVE); stale.setRequesterId(requesterId);
        stale.setTitle("Old"); stale.setStatus(ApprovalStatus.PENDING);
        stale.setCurrentStep(1);
        stale.setPayloadJson("{\"scopeVersion\":8,\"scopeHash\":\"" + "a".repeat(64) + "\"}");
        ApprovalStep staleStep = new ApprovalStep();
        staleStep.setRequest(stale);
        staleStep.setStepNumber(1);
        staleStep.setApproverRole("OPERATIONS");
        staleStep.setDecision(ApprovalDecision.PENDING);
        stale.getSteps().add(staleStep);
        when(requestRepository.findFirstPendingByTargetAndAction(
                ApprovalTargetType.PLANNED_SHUTDOWN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(Optional.of(stale));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(lifecycleRoleInputs(
                "CURRENT_SHUTDOWN_REVIEWER_ALPHA", "CURRENT_SHUTDOWN_REVIEWER_BETA"));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest saved = inv.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            if (saved.getCreatedAt() == null) ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            if (saved.getUpdatedAt() == null) ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.PLANNED_SHUTDOWN, targetId, ApprovalActionType.APPROVE, "retry"));

        assertThat(stale.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().getLast().getPayloadJson())
                .contains("\"scopeVersion\":9", "b".repeat(64));
        verify(governanceService).record(stale, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED,
                requesterId, "Superseded by a newer planned shutdown scope snapshot");
    }

    @Test
    void repairCampaignRetryCancelsStalePendingRequestAndCreatesCurrentSnapshot() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.REPAIR_CAMPAIGN, "RCMP-2026-001", "Pump overhaul");
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 9L, 9L, "b".repeat(64), 12L);
        ApprovalRequest stale = new ApprovalRequest();
        stale.setId(UUID.randomUUID());
        stale.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        stale.setTargetId(targetId);
        stale.setActionType(ApprovalActionType.APPROVE);
        stale.setRequesterId(requesterId);
        stale.setTitle("Old");
        stale.setStatus(ApprovalStatus.PENDING);
        stale.setCurrentStep(1);
        stale.setPayloadJson("{\"scopeVersion\":8,\"scopeHash\":\"" + "a".repeat(64)
                + "\",\"campaignVersion\":11}");
        int routeOrder = 1;
        for (String role : List.of("OLD_REVIEWER_ALPHA", "OLD_REVIEWER_BETA")) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(stale);
            step.setStepNumber(routeOrder++);
            step.setApproverRole(role);
            step.setDecision(ApprovalDecision.PENDING);
            stale.getSteps().add(step);
        }
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(stale));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(lifecycleRoleInputs(
                "CURRENT_REVIEWER_ALPHA", "CURRENT_REVIEWER_BETA", "CURRENT_REVIEWER_GAMMA"));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest saved = inv.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            if (saved.getCreatedAt() == null) ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            if (saved.getUpdatedAt() == null) ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, "retry"));

        assertThat(stale.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().getLast().getPayloadJson())
                .isEqualTo("{\"scopeVersion\":9,\"scopeHash\":\"" + "b".repeat(64)
                        + "\",\"campaignVersion\":12}");
        verify(governanceService).record(stale, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED,
                requesterId, "Superseded by a newer repair campaign scope snapshot");
    }

    @Test
    void repairCampaignRetryCancelsPendingRequestWithMalformedRuntimeRoute() {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        stubTargetMetadata(ApprovalTargetType.REPAIR_CAMPAIGN, "RCMP-2026-002", "Pump overhaul");
        stubRepairCampaignApprovalSnapshot("PENDING_APPROVAL", 9L, 9L, "b".repeat(64), 12L);
        ApprovalRequest stale = new ApprovalRequest();
        stale.setId(UUID.randomUUID());
        stale.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        stale.setTargetId(targetId);
        stale.setActionType(ApprovalActionType.APPROVE);
        stale.setRequesterId(requesterId);
        stale.setTitle("Old");
        stale.setStatus(ApprovalStatus.PENDING);
        stale.setCurrentStep(1);
        stale.setPayloadJson("{\"scopeVersion\":9,\"scopeHash\":\"" + "b".repeat(64)
                + "\",\"campaignVersion\":12}");
        ApprovalStep wrongStep = new ApprovalStep();
        wrongStep.setRequest(stale);
        wrongStep.setStepNumber(2);
        wrongStep.setApproverRole("REPAIR_CAMPAIGN_APPROVE");
        wrongStep.setDecision(ApprovalDecision.PENDING);
        stale.getSteps().add(wrongStep);
        when(requestRepository.findAllPendingByTargetAndAction(
                ApprovalTargetType.REPAIR_CAMPAIGN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(List.of(stale));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        List<CreateApprovalRequest.StepInput> configuredRoute = lifecycleRoleInputs(
                "CURRENT_REVIEWER_ALPHA", "CURRENT_REVIEWER_BETA", "CURRENT_REVIEWER_GAMMA");
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(configuredRoute);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest saved = inv.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            if (saved.getCreatedAt() == null) ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            if (saved.getUpdatedAt() == null) ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        ApprovalRequestDto result = service.requestApproval(new ApprovalStartRequest(
                ApprovalTargetType.REPAIR_CAMPAIGN, targetId, ApprovalActionType.APPROVE, "retry"));

        assertThat(stale.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(result.steps()).hasSize(configuredRoute.size());
        assertThat(result.steps()).extracting(ApprovalStepDto::approverRole)
                .containsExactly("CURRENT_REVIEWER_ALPHA", "CURRENT_REVIEWER_BETA",
                        "CURRENT_REVIEWER_GAMMA");
        verify(requestRepository, times(2)).saveAndFlush(any(ApprovalRequest.class));
        assertThat(stale.getFailureReason()).isEqualTo("NONCANONICAL_REPAIR_CAMPAIGN_ROUTE");
        verify(governanceService).record(stale, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED,
                requesterId, "NONCANONICAL_REPAIR_CAMPAIGN_ROUTE");
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
        verify(notificationService).notifyApprovalResult(
                any(), any(com.toir.dto.notification.NotificationContent.class), any(NotificationSeverity.class),
                eq(com.toir.enums.NotificationEventType.APPROVAL_RETURNED_TO_REQUESTER),
                any(), any(), eq(approvalId)
        );
        verify(notificationService).notifyUser(
                any(), any(com.toir.dto.notification.NotificationContent.class), any(NotificationSeverity.class),
                eq(com.toir.enums.NotificationEventType.APPROVAL_RETURNED_TO_APPROVER),
                eq("APPROVAL_REQUEST"), eq(approvalId.toString())
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

    @Test
    void failedFinalizationCanRetryItsOriginalApprovalDecision() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId,
                UUID.randomUUID(),
                1,
                approverId
        );
        ApprovalStep step = approval.getSteps().getFirst();
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(approverId);
        step.setDecidedAt(Instant.now().minusSeconds(30));
        approval.setStatus(ApprovalStatus.FAILED);
        approval.setFailureReason("Previous finalization failed");
        approval.setCompletedAt(Instant.now().minusSeconds(30));

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(approvalActionExecutor.execute(approval)).thenReturn("{\"status\":\"APPROVED\"}");
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.approve(
                approvalId,
                new com.toir.dto.approval.DecisionRequest(approverId, "retry")
        );

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.failureReason()).isNull();
        assertThat(result.resultJson()).isEqualTo("{\"status\":\"APPROVED\"}");
        verify(governanceService).record(
                approval,
                ApprovalStatus.FAILED,
                ApprovalStatus.APPROVED,
                approverId,
                "retry"
        );
    }

    @Test
    void terminalTransactionFailureIsPropagatedInsteadOfBeingSavedAsFailedApproval() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId,
                UUID.randomUUID(),
                1,
                approverId
        );

        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(approvalActionExecutor.execute(approval))
                .thenThrow(new UnexpectedRollbackException("domain finalizer transaction rolled back"));

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(approverId, "ok")))
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("domain finalizer transaction rolled back");

        verify(requestRepository, never()).save(approval);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getFailureReason()).isNull();
    }

    @Test
    void approvalFirstMaterializationBusinessFailureRollsBackTerminalDecision() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId,
                UUID.randomUUID(),
                1,
                approverId);
        approval.setTargetType(ApprovalTargetType.PPR_PLAN);
        approval.setTargetId(UUID.randomUUID());
        approval.setDocumentType(null);
        approval.setDocumentId(null);
        approval.setCalculationRevision(3L);
        approval.setCalculationContentHash("a".repeat(64));
        approval.setCalculationContentHashVersion(1);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId))
                .thenReturn(Optional.of(approval));
        when(maintenanceScheduleApprovalBindingService
                .isBoundApprovalFirstRequest(approval))
                .thenReturn(true);
        when(maintenanceScheduleApprovalBindingService
                .routeMatches(eq(approval), isNull()))
                .thenReturn(true);
        when(approvalActionExecutor.execute(approval))
                .thenThrow(new RestException(
                        "stale calculation",
                        org.springframework.http.HttpStatus.CONFLICT,
                        "PPR_CALCULATION_APPROVAL_STALE"));

        assertThatThrownBy(() -> service.approve(
                approvalId,
                new DecisionRequest(approverId, "approve")))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("PPR_CALCULATION_APPROVAL_STALE"));

        verify(requestRepository, never()).save(approval);
    }

    @Test
    void completedApprovalFirstRetryIsIdempotentForOriginalApprover() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId,
                UUID.randomUUID(),
                1,
                approverId);
        approval.setTargetType(ApprovalTargetType.PPR_PLAN);
        approval.setTargetId(UUID.randomUUID());
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setExecuted(true);
        approval.setCalculationRevision(3L);
        approval.setCalculationContentHash("a".repeat(64));
        approval.setCalculationContentHashVersion(1);
        ApprovalStep step = approval.getSteps().getFirst();
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(approverId);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId))
                .thenReturn(Optional.of(approval));
        when(maintenanceScheduleApprovalBindingService
                .isBoundApprovalFirstRequest(approval))
                .thenReturn(true);

        ApprovalRequestDto result = service.approve(
                approvalId,
                new DecisionRequest(approverId, "retry"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.APPROVED);
        verifyNoInteractions(approvalActionExecutor);
        verify(requestRepository, never()).save(approval);
    }

    @Test
    void unrelatedDomainFinalizerExceptionKeepsExistingFailedRequestSemantics() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = pendingMultiStepApproval(
                approvalId, UUID.randomUUID(), 1, approverId);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(approvalActionExecutor.execute(approval))
                .thenThrow(new IllegalStateException("ordinary finalization failed"));
        when(requestRepository.save(approval)).thenReturn(approval);

        ApprovalRequestDto result = service.approve(
                approvalId, new DecisionRequest(approverId, "approve"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("ordinary finalization failed");
        verify(requestRepository).save(approval);
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

    private UpdateApprovalRequest validUpdate() {
        return new UpdateApprovalRequest(
                "Updated approval",
                "Updated description",
                List.of(new CreateApprovalRequest.StepInput(UUID.randomUUID(), null))
        );
    }

    private void stubSuccessfulUpdate(UUID approvalId, ApprovalRequest approval, UUID actorId) {
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(requestRepository.saveAndFlush(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenReturn(Optional.empty());
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

    private ApprovalRequest parallelApproval(UUID id, int round, UUID... approverIds) {
        ApprovalRequest approval = pendingApproval(id, UUID.randomUUID(), UUID.randomUUID());
        approval.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        approval.setApprovalRound(round);
        approval.setCurrentStep(0);
        approval.getSteps().clear();
        for (int index = 0; index < approverIds.length; index++) {
            ApprovalStep step = new ApprovalStep();
            step.setId(UUID.randomUUID());
            step.setRequest(approval);
            step.setStepNumber(index + 1);
            step.setApprovalRound(round);
            step.setFlowType(ApprovalFlowType.PARALLEL_ALL);
            step.setApproverId(approverIds[index]);
            step.setDecision(ApprovalDecision.PENDING);
            approval.getSteps().add(step);
        }
        return approval;
    }

    private void decideParallelVotes(ApprovalRequest approval, int approved, int rejected) {
        for (int index = 0; index < approved; index++) {
            approval.getSteps().get(index).setDecision(ApprovalDecision.APPROVED);
        }
        for (int index = approved; index < approved + rejected; index++) {
            approval.getSteps().get(index).setDecision(ApprovalDecision.REJECTED);
        }
    }

    private void stubParallelLock(UUID approvalId, ApprovalRequest approval) {
        org.mockito.Mockito.lenient().when(requestRepository.findByIdAndIsDeletedFalseForUpdate(approvalId))
                .thenReturn(Optional.of(approval));
        org.mockito.Mockito.lenient().when(requestRepository.findByIdAndIsDeletedFalse(approvalId))
                .thenReturn(Optional.of(approval));
        org.mockito.Mockito.lenient().when(userRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenReturn(Optional.empty());
    }

    private ApprovalRequest pendingRoleOnlyApproval(UUID id, UUID requesterId, String approverRole) {
        ApprovalRequest approval = pendingApproval(id, UUID.randomUUID(), requesterId);
        ApprovalStep step = new ApprovalStep();
        step.setRequest(approval);
        step.setStepNumber(1);
        step.setApproverId(null);
        step.setApproverRole(approverRole);
        step.setDecision(ApprovalDecision.PENDING);
        approval.getSteps().add(step);
        return approval;
    }

    private ApprovalRequest repairCampaignPending(UUID id,
                                                  UUID targetId,
                                                  UUID requesterId,
                                                  String payload,
                                                  List<String> roles) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setId(id);
        approval.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        approval.setTargetId(targetId);
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.setRequesterId(requesterId);
        approval.setTitle("Repair campaign approval");
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);
        approval.setPayloadJson(payload);
        int stepNumber = 1;
        for (String role : roles) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(approval);
            step.setStepNumber(stepNumber++);
            step.setApproverRole(role);
            step.setDecision(ApprovalDecision.PENDING);
            approval.getSteps().add(step);
        }
        return approval;
    }

    private static List<CreateApprovalRequest.StepInput> lifecycleRoleInputs(String... roles) {
        return java.util.Arrays.stream(roles)
                .map(role -> new CreateApprovalRequest.StepInput(null, role))
                .toList();
    }

    private ApprovalRequest lifecyclePending(UUID id,
                                             ApprovalTargetType targetType,
                                             UUID targetId,
                                             String payload,
                                             CreateApprovalRequest.StepInput... inputs) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setId(id);
        ReflectionTestUtils.setField(approval, "createdAt", Instant.now());
        ReflectionTestUtils.setField(approval, "updatedAt", Instant.now());
        approval.setTargetType(targetType);
        approval.setTargetId(targetId);
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.setRequesterId(UUID.randomUUID());
        approval.setTitle(targetType.name() + " approval");
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);
        approval.setPayloadJson(payload);
        approval.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        int stepNumber = 1;
        for (CreateApprovalRequest.StepInput input : inputs) {
            ApprovalStep step = new ApprovalStep();
            step.setId(UUID.randomUUID());
            step.setRequest(approval);
            step.setStepNumber(stepNumber++);
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            step.setDecision(ApprovalDecision.PENDING);
            approval.getSteps().add(step);
        }
        return approval;
    }

    private ApprovalRequest lifecycleRoleApproval(UUID id,
                                                  ApprovalTargetType targetType,
                                                  UUID requesterId,
                                                  String role) {
        ApprovalRequest approval = lifecyclePending(
                id,
                targetType,
                UUID.randomUUID(),
                "{}",
                new CreateApprovalRequest.StepInput(null, role));
        approval.setRequesterId(requesterId);
        return approval;
    }

    private ApprovalRequest lifecycleExplicitApproval(UUID id,
                                                      ApprovalTargetType targetType,
                                                      UUID requesterId,
                                                      UUID approverId) {
        ApprovalRequest approval = lifecyclePending(
                id,
                targetType,
                UUID.randomUUID(),
                "{}",
                new CreateApprovalRequest.StepInput(approverId, null));
        approval.setRequesterId(requesterId);
        return approval;
    }

    private ApprovalRequest lifecycleApprovalWithApprovedFirstStep(UUID id,
                                                                   ApprovalTargetType targetType,
                                                                   UUID requesterId,
                                                                   UUID approvedActorId,
                                                                   String currentRole) {
        ApprovalRequest approval = lifecyclePending(
                id,
                targetType,
                UUID.randomUUID(),
                "{}",
                new CreateApprovalRequest.StepInput(null, "FIRST_REVIEWER"),
                new CreateApprovalRequest.StepInput(null, currentRole));
        approval.setRequesterId(requesterId);
        approval.setCurrentStep(2);
        ApprovalStep first = approval.getSteps().getFirst();
        first.setDecision(ApprovalDecision.APPROVED);
        first.setDecidedById(approvedActorId);
        first.setDecidedAt(Instant.now().minusSeconds(30));
        return approval;
    }

    private void stubLifecycleMutationLock(
            UUID approvalId,
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalRequest reread) {
        when(jdbcTemplate.query(eq(LIFECYCLE_IDENTITY_SQL), any(RowMapper.class), eq(approvalId)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
                    when(rs.getString("target_type")).thenReturn(targetType.name());
                    when(rs.getObject("target_id", UUID.class)).thenReturn(targetId);
                    when(rs.getString("action_type")).thenReturn(ApprovalActionType.APPROVE.name());
                    return List.of(mapper.mapRow(rs, 0));
                });
        when(jdbcTemplate.query(eq(lifecycleDomainLockSql(targetType)), any(RowMapper.class), eq(targetId)))
                .thenReturn(List.of(targetId));
        when(requestRepository.findByIdAndIsDeletedFalseForUpdate(approvalId))
                .thenReturn(Optional.of(reread));
    }

    private static String lifecycleDomainLockSql(ApprovalTargetType targetType) {
        return targetType == ApprovalTargetType.REPAIR_CAMPAIGN
                ? "SELECT id FROM repair_campaigns WHERE id = ? AND is_deleted = false FOR UPDATE"
                : "SELECT id FROM planned_shutdowns WHERE id = ? AND is_deleted = false FOR UPDATE";
    }

    private void stubLifecycleActor(UUID approvalId,
                                    ApprovalRequest approval,
                                    UUID actorId,
                                    String actorRole) {
        User actor = activeUserWithRole(actorId, actorRole);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(userRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return actorId.equals(userId) ? Optional.of(actor) : Optional.empty();
        });
    }

    private User activeUserWithRole(UUID userId, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(userId);
        user.setFullName("Approver " + roleCode);
        user.setStatus(UserStatus.ACTIVE);
        user.setPrimaryRole(role);
        return user;
    }

    private void stubTargetMetadata(ApprovalTargetType targetType, String code, String title) {
        when(jdbcTemplate.query(
                eq(targetMetadataSql(targetType)),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
        )).thenAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(2);
            java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("title")).thenReturn(title);
            when(rs.getString("code")).thenReturn(code);
            return extractor.extractData(rs);
        });
    }

    private void stubRepairCampaignApprovalSnapshot(String status,
                                                    Long scopeVersion,
                                                    Long approvalScopeVersion,
                                                    String scopeHash,
                                                    Long campaignVersion) {
        when(jdbcTemplate.queryForObject(
                eq("""
                select status, scope_version, approval_scope_version, approval_scope_hash, version
                from repair_campaigns where id = ? and is_deleted = false
                """),
                any(RowMapper.class),
                any(UUID.class)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(rs.getString("status")).thenReturn(status);
            when(rs.getLong("scope_version")).thenReturn(scopeVersion);
            when(rs.getLong("approval_scope_version")).thenReturn(approvalScopeVersion);
            when(rs.getString("approval_scope_hash")).thenReturn(scopeHash);
            when(rs.getLong("version")).thenReturn(campaignVersion);
            return mapper.mapRow(rs, 0);
        });
    }

    private String targetMetadataSql(ApprovalTargetType targetType) {
        return switch (targetType) {
            case WORK_ORDER -> "select number as code, title as title from work_orders where id = ? and is_deleted = false";
            case PPR_PLAN -> "select code as code, name as title from ppr_plans where id = ? and is_deleted = false";
            case PROCUREMENT_REQUEST, PROCUREMENT -> "select number as code, title as title from procurement_requests where id = ? and is_deleted = false";
            case MAINTENANCE_BUDGET, BUDGET -> "select code as code, name as title from maintenance_budgets where id = ? and is_deleted = false";
            case REPAIR_REQUEST -> "select number as code, title as title from repair_requests where id = ? and is_deleted = false";
            case MAINTENANCE_REGULATION -> "select code as code, name as title from maintenance_regulations where id = ? and is_deleted = false";
            case REGULATION_CHANGE_PROPOSAL -> "select code as code, title as title from regulation_change_proposals where id = ? and is_deleted = false";
            case ACTUAL_COST -> "select null as code, concat('Actual cost ', amount) as title from actual_costs where id = ? and is_deleted = false";
            case DEFECT_LIST -> "select code as code, title as title from defect_lists where id = ? and is_deleted = false";
            case PLANNED_SHUTDOWN -> "select null as code, name as title from planned_shutdowns where id = ? and is_deleted = false";
            case REPAIR_CAMPAIGN -> "select code as code, name as title from repair_campaigns where id = ? and is_deleted = false";
            default -> null;
        };
    }
}
