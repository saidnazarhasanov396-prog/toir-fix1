package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ApprovalStartRequest;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.ReturnApprovalRequest;
import com.toir.dto.approval.UpdateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
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
import com.toir.service.maintanance.MaintenanceRegulationService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    ObjectProvider<MaintenanceRegulationService> maintenanceRegulationServiceProvider;

    @Mock
    MaintenanceRegulationService maintenanceRegulationService;

    @InjectMocks
    ApprovalService service;

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
    void plannedShutdownApprovalCapturesCurrentScopeVersionInCanonicalRequest() {
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
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(List.of(
                new CreateApprovalRequest.StepInput(null,
                        com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE),
                new CreateApprovalRequest.StepInput(null,
                        com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE)));
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
                .containsExactly(
                        com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE,
                        com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE);
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
        stale.setPayloadJson("{\"scopeVersion\":8,\"scopeHash\":\"" + "a".repeat(64) + "\"}");
        when(requestRepository.findFirstPendingByTargetAndAction(
                ApprovalTargetType.PLANNED_SHUTDOWN.name(), targetId,
                ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name()))
                .thenReturn(Optional.of(stale));
        when(slaPolicyService.slaFor(any(ApprovalRequest.class))).thenReturn(Duration.ofHours(24));
        when(routeResolver.resolveRoute(any(ApprovalRequest.class))).thenReturn(List.of(
                new CreateApprovalRequest.StepInput(null,
                        com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE),
                new CreateApprovalRequest.StepInput(null,
                        com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE)));
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
