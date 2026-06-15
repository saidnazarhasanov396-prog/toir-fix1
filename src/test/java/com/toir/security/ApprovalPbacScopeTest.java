package com.toir.security;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalDelegate;
import com.toir.entity.ApprovalHistory;
import com.toir.entity.ApprovalStep;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.ApprovalDelegateRepository;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.ApprovalScopeService;
import com.toir.service.ApprovalService;
import com.toir.service.FinanceScopeService;
import com.toir.service.NotificationService;
import com.toir.service.PprPlanService;
import com.toir.service.WorkOrderService;
import com.toir.service.approval.DefaultApprovalActionExecutor;
import com.toir.service.approval.DefaultApprovalRouteResolver;
import com.toir.service.approval.MaintenanceDueEventApprovalHandler;
import com.toir.service.approval.MaintenanceBudgetApprovalHandler;
import com.toir.service.approval.PprPlanApprovalHandler;
import com.toir.service.approval.ProcurementRequestApprovalHandler;
import com.toir.service.approval.WorkOrderApprovalHandler;
import com.toir.service.approval.ApprovalGovernanceService;
import com.toir.service.approval.ApprovalSlaPolicyService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalPbacScopeTest {

    ApprovalRequestRepository requestRepository;
    ApprovalDelegateRepository delegateRepository;
    ApprovalTemplateRepository templateRepository;
    UserRepository userRepository;
    AuditBuilderService auditBuilderService;
    ApprovalScopeService approvalScopeService;
    ProcurementRequestRepository procurementRequestRepository;
    MaintenanceBudgetRepository maintenanceBudgetRepository;
    WorkOrderService workOrderService;
    PprPlanService pprPlanService;
    MaintenanceAutomationService maintenanceAutomationService;
    ScopeAccessService scopeAccessService;
    NotificationService notificationService;
    ApprovalGovernanceService governanceService;
    ApprovalSlaPolicyService slaPolicyService;
    JdbcTemplate jdbcTemplate;
    ApprovalService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(ApprovalRequestRepository.class);
        delegateRepository = mock(ApprovalDelegateRepository.class);
        templateRepository = mock(ApprovalTemplateRepository.class);
        userRepository = mock(UserRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        approvalScopeService = mock(ApprovalScopeService.class);
        procurementRequestRepository = mock(ProcurementRequestRepository.class);
        maintenanceBudgetRepository = mock(MaintenanceBudgetRepository.class);
        workOrderService = mock(WorkOrderService.class);
        pprPlanService = mock(PprPlanService.class);
        maintenanceAutomationService = mock(MaintenanceAutomationService.class);
        scopeAccessService = mock(ScopeAccessService.class);
        notificationService = mock(NotificationService.class);
        governanceService = mock(ApprovalGovernanceService.class);
        slaPolicyService = mock(ApprovalSlaPolicyService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        when(slaPolicyService.slaFor(any())).thenReturn(Duration.ofHours(24));
        service = new ApprovalService(
                requestRepository,
                delegateRepository,
                new DefaultApprovalActionExecutor(List.of(
                        new WorkOrderApprovalHandler(workOrderService),
                        new PprPlanApprovalHandler(pprPlanService),
                        new ProcurementRequestApprovalHandler(procurementRequestRepository),
                        new MaintenanceBudgetApprovalHandler(maintenanceBudgetRepository),
                        new MaintenanceDueEventApprovalHandler(maintenanceAutomationService)
                )),
                governanceService,
                slaPolicyService,
                new DefaultApprovalRouteResolver(templateRepository, userRepository),
                jdbcTemplate,
                auditBuilderService,
                approvalScopeService,
                scopeAccessService,
                notificationService,
                userRepository
        );
    }

    @Test
    void requesterCanReadOwnApproval() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        service.findById(approvalId);

        verify(approvalScopeService).assertCanReadApproval(approval);
    }

    @Test
    void unrelatedUserCannotReadApproval() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(approvalScopeService).assertCanReadApproval(approval);

        assertThatThrownBy(() -> service.findById(approvalId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingApprovalRemains404() {
        UUID approvalId = UUID.randomUUID();
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(approvalId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Approval request not found");
    }

    @Test
    void queueFiltersOutOutOfScopeApprovalsForNonAdmin() {
        ApprovalRequest allowed = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ApprovalRequest denied = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(allowed, denied));
        when(approvalScopeService.canReadApproval(allowed)).thenReturn(true);
        when(approvalScopeService.canReadApproval(denied)).thenReturn(false);

        var result = service.search(null, null, null, ApprovalStatus.PENDING, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(allowed.getId());
    }

    @Test
    void searchCombinesDocumentRequesterStatusAndSearchFilters() {
        UUID documentId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest match = approval(UUID.randomUUID(), requesterId, UUID.randomUUID(), documentId);
        match.setTitle("Pump replacement approval");
        ApprovalRequest wrongStatus = approval(UUID.randomUUID(), requesterId, UUID.randomUUID(), documentId);
        wrongStatus.setStatus(ApprovalStatus.APPROVED);
        ApprovalRequest wrongRequester = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), documentId);
        ApprovalRequest wrongDocument = approval(UUID.randomUUID(), requesterId, UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(match, wrongStatus, wrongRequester, wrongDocument));
        when(approvalScopeService.canReadApproval(match)).thenReturn(true);

        var result = service.search("work-order", documentId, requesterId, ApprovalStatus.PENDING, "pump");

        assertThat(result).extracting(ApprovalRequestDto::id).containsExactly(match.getId());
    }

    @Test
    void searchMatchesDescriptionStatusAndStepFields() {
        ApprovalRequest descriptionMatch = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        descriptionMatch.setDescription("Urgent safety review");
        ApprovalRequest statusMatch = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        statusMatch.setStatus(ApprovalStatus.CANCELLED);
        ApprovalRequest stepCommentMatch = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        stepCommentMatch.getSteps().getFirst().setComment("Pump inspection comment");
        ApprovalRequest stepRoleMatch = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        stepRoleMatch.getSteps().getFirst().setApproverRole("CHIEF_MECHANIC");
        when(requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(descriptionMatch, statusMatch, stepCommentMatch, stepRoleMatch));
        when(approvalScopeService.canReadApproval(any())).thenReturn(true);

        assertThat(service.search(null, null, null, null, "safety")).extracting(ApprovalRequestDto::id)
                .containsExactly(descriptionMatch.getId());
        assertThat(service.search(null, null, null, null, "cancelled")).extracting(ApprovalRequestDto::id)
                .containsExactly(statusMatch.getId());
        assertThat(service.search(null, null, null, null, "inspection")).extracting(ApprovalRequestDto::id)
                .containsExactly(stepCommentMatch.getId());
        assertThat(service.search(null, null, null, null, "mechanic")).extracting(ApprovalRequestDto::id)
                .containsExactly(stepRoleMatch.getId());
    }

    @Test
    void listByDocumentNormalizesDocumentTypeBeforeQuerying() {
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), documentId);
        when(requestRepository.findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse("WORK_ORDER", documentId))
                .thenReturn(List.of(approval));
        when(approvalScopeService.canReadApproval(approval)).thenReturn(true);

        var result = service.listByDocument("work-order", documentId);

        assertThat(result).hasSize(1);
        verify(requestRepository).findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse("WORK_ORDER", documentId);
    }

    @Test
    void createValidatesRequesterAndDocumentScope() {
        CreateApprovalRequest request = createRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(request);

        verify(approvalScopeService).assertCanCreateApproval(request);
    }

    @Test
    void existingApprovalCreationAliasesDocumentFieldsToTargetFields() {
        UUID documentId = UUID.randomUUID();
        CreateApprovalRequest request = createRequest(UUID.randomUUID(), UUID.randomUUID(), documentId);
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(request);

        org.mockito.ArgumentCaptor<ApprovalRequest> captor = org.mockito.ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(captor.capture());
        ApprovalRequest saved = captor.getValue();
        assertThat(saved.getDocumentType()).isEqualTo("WORK_ORDER");
        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getTargetType()).isEqualTo(ApprovalTargetType.WORK_ORDER);
        assertThat(saved.getTargetId()).isEqualTo(documentId);
    }

    @Test
    void newFrameworkFieldsCanBeSavedWithoutChangingLegacyDocumentFields() {
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), documentId);
        approval.setTargetType(ApprovalTargetType.MAINTENANCE_BUDGET);
        approval.setTargetId(documentId);
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.setPayloadJson("{\"source\":\"test\"}");
        approval.setResultJson("{\"status\":\"queued\"}");
        approval.setFailureReason("not executed");
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest saved = requestRepository.save(approval);

        assertThat(saved.getDocumentType()).isEqualTo("WORK_ORDER");
        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getTargetType()).isEqualTo(ApprovalTargetType.MAINTENANCE_BUDGET);
        assertThat(saved.getTargetId()).isEqualTo(documentId);
        assertThat(saved.getActionType()).isEqualTo(ApprovalActionType.APPROVE);
        assertThat(saved.getPayloadJson()).contains("source");
        assertThat(saved.getResultJson()).contains("queued");
        assertThat(saved.getFailureReason()).isEqualTo("not executed");
    }

    @Test
    void createNotifiesCurrentApproverWithoutChangingApprovalScopeValidation() {
        UUID documentId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        CreateApprovalRequest request = createRequest(requesterId, approverId, documentId);
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(request);

        verify(approvalScopeService).assertCanCreateApproval(request);
        verify(notificationService).notifyUser(
                eq(approverId),
                org.mockito.ArgumentMatchers.contains("Approval requested"),
                org.mockito.ArgumentMatchers.contains("requires your decision"),
                eq(com.toir.enums.NotificationSeverity.INFO),
                eq("ApprovalRequest"),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void currentPendingApproverCanApprove() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        verify(approvalScopeService).assertCanDecideApproval(approval, approval.getSteps().getFirst());
        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        verify(notificationService).notifyUser(
                eq(approval.getRequesterId()),
                org.mockito.ArgumentMatchers.contains("Approval approved"),
                org.mockito.ArgumentMatchers.contains("approved"),
                eq(com.toir.enums.NotificationSeverity.INFO),
                eq("WorkOrder"),
                eq(approval.getDocumentId().toString())
        );
    }

    @Test
    void requesterWhoIsNotApproverCannotApprove() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(approvalScopeService).assertCanDecideApproval(approval, approval.getSteps().getFirst());

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(approverId, "spoof")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requestSuppliedApproverIdCannotSpoofDecision() {
        UUID approvalId = UUID.randomUUID();
        UUID currentApproverId = UUID.randomUUID();
        UUID spoofedApproverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), currentApproverId, UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(spoofedApproverId, "spoof")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only designated approver");
    }

    @Test
    void alreadyDecidedApprovalStillUsesExistingStatusValidation() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, UUID.randomUUID());
        approval.setStatus(ApprovalStatus.APPROVED);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(approverId, "late")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Request is not pending");
    }

    @Test
    void expiredApprovalCannotBeApprovedAndNoBusinessActionRuns() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, UUID.randomUUID());
        approval.setExpiresAt(Instant.now().minusSeconds(60));
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(governanceService.expire(eq(approval), any(), any())).thenAnswer(invocation -> {
            approval.setStatus(ApprovalStatus.EXPIRED);
            return approval;
        });

        assertThatThrownBy(() -> service.approve(approvalId, new DecisionRequest(approverId, "late")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Request is not pending");

        verify(governanceService).expire(eq(approval), any(), any());
        verify(workOrderService, never()).approve(any(), any());
    }

    @Test
    void activeDelegateCanApproveForDesignatedApprover() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID delegateId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        ApprovalDelegate delegate = new ApprovalDelegate();
        delegate.setApproverId(approverId);
        delegate.setDelegateId(delegateId);
        delegate.setActiveFrom(Instant.now().minusSeconds(60));
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(delegateRepository.findActiveDelegation(eq(approverId), eq(delegateId), any())).thenReturn(Optional.of(delegate));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(delegateId);
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(approvalId, new DecisionRequest(delegateId, "delegated ok"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        verify(workOrderService).approve(documentId, delegateId);
        verify(approvalScopeService, never()).assertCanDecideApproval(any(), any());
    }

    @Test
    void cancelValidatesApprovalScopeBeforeStatusTransition() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancel(approvalId);

        verify(approvalScopeService).assertCanCancelApproval(approval);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
    }

    @Test
    void statisticsReturnsApprovalCounts() {
        when(requestRepository.countByStatus(ApprovalStatus.PENDING)).thenReturn(3L);
        when(requestRepository.countByStatus(ApprovalStatus.APPROVED)).thenReturn(4L);
        when(requestRepository.countByStatus(ApprovalStatus.REJECTED)).thenReturn(5L);
        when(requestRepository.countByStatus(ApprovalStatus.EXPIRED)).thenReturn(6L);
        when(requestRepository.countEscalated()).thenReturn(2L);

        var result = service.statistics();

        assertThat(result.pendingCount()).isEqualTo(3L);
        assertThat(result.approvedCount()).isEqualTo(4L);
        assertThat(result.rejectedCount()).isEqualTo(5L);
        assertThat(result.expiredCount()).isEqualTo(6L);
        assertThat(result.escalatedCount()).isEqualTo(2L);
    }

    @Test
    void historyRequiresApprovalReadScopeAndReturnsHistoryEntries() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, UUID.randomUUID());
        ApprovalHistory history = new ApprovalHistory();
        history.setId(UUID.randomUUID());
        history.setApprovalId(approvalId);
        history.setOldStatus(ApprovalStatus.PENDING);
        history.setNewStatus(ApprovalStatus.APPROVED);
        history.setChangedBy(approverId);
        history.setChangedAt(Instant.now());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(governanceService.history(approvalId)).thenReturn(List.of(history));

        var result = service.history(approvalId);

        verify(approvalScopeService).assertCanReadApproval(approval);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().newStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void scopeAllowsRequesterByCurrentUserId() {
        UUID userId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID());
        ScopeAccessService scopeAccessService = scopeAccessService(userId, Optional.empty(), false);

        assertThat(scope(scopeAccessService).canReadApproval(approval)).isTrue();
    }

    @Test
    void scopeAllowsRequesterByDerivedEmployeeId() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), employeeId, UUID.randomUUID(), UUID.randomUUID());
        ScopeAccessService scopeAccessService = scopeAccessService(UUID.randomUUID(), Optional.of(employeeId), false);

        assertThat(scope(scopeAccessService).canReadApproval(approval)).isTrue();
    }

    @Test
    void scopeAllowsCurrentPendingApproverByCurrentUserId() {
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), UUID.randomUUID(), approverId, UUID.randomUUID());
        ScopeAccessService scopeAccessService = scopeAccessService(approverId, Optional.empty(), false);

        assertThat(scope(scopeAccessService).canReadApproval(approval)).isTrue();
    }

    @Test
    void scopeDeniesUnrelatedApproval() {
        ApprovalRequest approval = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScopeAccessService scopeAccessService = scopeAccessService(UUID.randomUUID(), Optional.empty(), false);

        assertThat(scope(scopeAccessService).canReadApproval(approval)).isFalse();
        assertThatThrownBy(() -> scope(scopeAccessService).assertCanReadApproval(approval))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void scopeAdminAndWildcardBypassApprovalScope() {
        ApprovalRequest approval = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ScopeAccessService scopeAccessService = scopeAccessService(UUID.randomUUID(), Optional.empty(), true);

        assertThat(scope(scopeAccessService).canReadApproval(approval)).isTrue();
        scope(scopeAccessService).assertCanCreateApproval(createRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        scope(scopeAccessService).assertCanDecideApproval(approval, approval.getSteps().getFirst());
        scope(scopeAccessService).assertCanCancelApproval(approval);
    }

    @Test
    void departmentScopedUserCanReadLinkedWorkOrderApproval() {
        UUID documentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), documentId);
        ScopeAccessService scopeAccessService = scopeAccessService(UUID.randomUUID(), Optional.empty(), false);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        when(workOrderRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(workOrder(departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        assertThat(scope(scopeAccessService, workOrderRepository).canReadApproval(approval)).isTrue();
    }

    @Test
    void createAllowsSelfRequester() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ScopeAccessService scopeAccessService = scopeAccessService(userId, Optional.empty(), false);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        when(workOrderRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(workOrder(departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        scope(scopeAccessService, workOrderRepository)
                .assertCanCreateApproval(createRequest(userId, UUID.randomUUID(), documentId));
    }

    @Test
    void createDeniesAnotherRequesterForNonAdmin() {
        ScopeAccessService scopeAccessService = scopeAccessService(UUID.randomUUID(), Optional.empty(), false);

        assertThatThrownBy(() -> scope(scopeAccessService)
                .assertCanCreateApproval(createRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createDeniesLinkedDocumentOutOfScopeWhenResolverExists() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ScopeAccessService scopeAccessService = scopeAccessService(userId, Optional.empty(), false);
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        when(workOrderRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(workOrder(departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> scope(scopeAccessService, workOrderRepository)
                .assertCanCreateApproval(createRequest(userId, UUID.randomUUID(), documentId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void currentPendingApproverCanDecideAndRequesterCannotDecide() {
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), requesterId, approverId, UUID.randomUUID());

        scope(scopeAccessService(approverId, Optional.empty(), false))
                .assertCanDecideApproval(approval, approval.getSteps().getFirst());
        assertThatThrownBy(() -> scope(scopeAccessService(requesterId, Optional.empty(), false))
                .assertCanDecideApproval(approval, approval.getSteps().getFirst()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void currentPendingApproverCanReject() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, UUID.randomUUID());
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reject(approvalId, new DecisionRequest(approverId, "no"));

        verify(approvalScopeService).assertCanDecideApproval(approval, approval.getSteps().getFirst());
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getSteps().getFirst().getDecision()).isEqualTo(ApprovalDecision.REJECTED);
    }

    @Test
    void requesterCanCancelOwnApprovalAndUnrelatedUserCannotCancel() {
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest approval = approval(UUID.randomUUID(), requesterId, UUID.randomUUID(), UUID.randomUUID());

        scope(scopeAccessService(requesterId, Optional.empty(), false)).assertCanCancelApproval(approval);
        assertThatThrownBy(() -> scope(scopeAccessService(UUID.randomUUID(), Optional.empty(), false))
                .assertCanCancelApproval(approval))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createOrReuseReturnsExistingPendingApprovalForIntegratedDocument() {
        UUID documentId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest existing = approval(UUID.randomUUID(), requesterId, approverId, documentId);
        existing.setDocumentType("WORK_ORDER");
        when(requestRepository.findFirstPendingByTargetAndAction(
                "WORK_ORDER",
                documentId,
                ApprovalActionType.APPROVE.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.of(existing));

        var result = service.createOrReuseApprovalForDocument(
                "WORK_ORDER",
                documentId,
                requesterId,
                approverId,
                "WORK_ORDER_APPROVER",
                "Work order approval",
                "Approval request"
        );

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(requestRepository, never()).save(any(ApprovalRequest.class));
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void procurementApprovalFlowCreatesApprovalRequest() {
        UUID documentId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(requestRepository.findFirstPendingByTargetAndAction(
                "PROCUREMENT_REQUEST",
                documentId,
                ApprovalActionType.APPROVE.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.createOrReuseApprovalForDocument(
                "PROCUREMENT_REQUEST",
                documentId,
                requesterId,
                approverId,
                "PROCUREMENT_APPROVER",
                "Procurement approval",
                "Approval request"
        );

        assertThat(result.documentType()).isEqualTo("PROCUREMENT_REQUEST");
        assertThat(result.documentId()).isEqualTo(documentId);
        org.mockito.ArgumentCaptor<ApprovalRequest> captor = org.mockito.ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(ApprovalTargetType.PROCUREMENT_REQUEST);
        assertThat(captor.getValue().getTargetId()).isEqualTo(documentId);
    }

    @Test
    void maintenanceBudgetApprovalFlowCreatesApprovalRequest() {
        UUID documentId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(requestRepository.findFirstPendingByTargetAndAction(
                "MAINTENANCE_BUDGET",
                documentId,
                ApprovalActionType.APPROVE.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.createOrReuseApprovalForDocument(
                "MAINTENANCE_BUDGET",
                documentId,
                requesterId,
                approverId,
                "BUDGET_APPROVER",
                "Budget approval",
                "Approval request"
        );

        assertThat(result.documentType()).isEqualTo("MAINTENANCE_BUDGET");
        assertThat(result.documentId()).isEqualTo(documentId);
        org.mockito.ArgumentCaptor<ApprovalRequest> captor = org.mockito.ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(ApprovalTargetType.MAINTENANCE_BUDGET);
        assertThat(captor.getValue().getTargetId()).isEqualTo(documentId);
    }

    @Test
    void maintenanceDueEventApprovalFlowCreatesApprovalRequestWithRequestedAction() {
        UUID eventId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(requestRepository.findFirstPendingByTargetAndAction(
                "MAINTENANCE_DUE_EVENT",
                eventId,
                ApprovalActionType.CREATE_WORK_ORDER.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.createOrReuseApprovalForDocument(
                "MAINTENANCE_DUE_EVENT",
                eventId,
                ApprovalActionType.CREATE_WORK_ORDER,
                requesterId,
                approverId,
                "MAINTENANCE_EVENT_APPROVER",
                "Maintenance due event approval",
                "Approval request"
        );

        assertThat(result.documentType()).isEqualTo("MAINTENANCE_DUE_EVENT");
        assertThat(result.documentId()).isEqualTo(eventId);
        org.mockito.ArgumentCaptor<ApprovalRequest> captor = org.mockito.ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(ApprovalTargetType.MAINTENANCE_DUE_EVENT);
        assertThat(captor.getValue().getTargetId()).isEqualTo(eventId);
        assertThat(captor.getValue().getActionType()).isEqualTo(ApprovalActionType.CREATE_WORK_ORDER);
    }

    @Test
    void maintenanceDueEventDuplicatePendingApprovalIsReusedForSameAction() {
        UUID eventId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest existing = approval(UUID.randomUUID(), requesterId, approverId, eventId);
        existing.setDocumentType("MAINTENANCE_DUE_EVENT");
        existing.setActionType(ApprovalActionType.CREATE_TASK);
        when(requestRepository.findFirstPendingByTargetAndAction(
                "MAINTENANCE_DUE_EVENT",
                eventId,
                ApprovalActionType.CREATE_TASK.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.of(existing));

        var result = service.createOrReuseApprovalForDocument(
                "MAINTENANCE_DUE_EVENT",
                eventId,
                ApprovalActionType.CREATE_TASK,
                requesterId,
                approverId,
                "MAINTENANCE_EVENT_APPROVER",
                "Maintenance due event approval",
                "Approval request"
        );

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(requestRepository, never()).save(any(ApprovalRequest.class));
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
    }

    @Test
    void finalApprovalStepAppliesWorkOrderApprovalHandler() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("WORK_ORDER");
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        verify(workOrderService).approve(documentId, approverId);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.isExecuted()).isTrue();
        assertThat(approval.getExecutedAt()).isNotNull();
        assertThat(approval.getExecutionId()).isNotNull();
    }

    @Test
    void rejectedWorkOrderApprovalDoesNotApproveWorkOrder() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("WORK_ORDER");
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reject(approvalId, new DecisionRequest(approverId, "no"));

        verify(workOrderService, never()).approve(any(), any());
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.isExecuted()).isTrue();
    }

    @Test
    void finalApprovalStepAppliesPprPlanApprovalHandler() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("PPR_PLAN");
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        verify(pprPlanService).approve(documentId, approverId);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void finalApprovalStepAppliesProcurementApprovalHandler() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("PROCUREMENT_REQUEST");
        ProcurementRequest procurementRequest = procurementRequest(documentId, ProcurementRequestStatus.SUBMITTED);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(procurementRequest));
        when(procurementRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.APPROVE);
        assertThat(procurementRequest.getStatus()).isEqualTo(ProcurementRequestStatus.APPROVED);
        assertThat(procurementRequest.getRejectionReason()).isNull();
        assertThat(procurementRequest.getApprovedAt()).isNotNull();
    }

    @Test
    void finalizerFailurePersistsFailedApprovalWithFailureReason() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("PROCUREMENT_REQUEST");
        ProcurementRequest procurementRequest = procurementRequest(documentId, ProcurementRequestStatus.DRAFT);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(procurementRequest));

        ApprovalRequestDto result = service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.FAILED);
        assertThat(result.failureReason())
                .isEqualTo("Procurement request approval can be finalized only from SUBMITTED status");
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.FAILED);
        assertThat(approval.getFailureReason())
                .isEqualTo("Procurement request approval can be finalized only from SUBMITTED status");
        assertThat(approval.getResultJson()).isNull();
        verify(requestRepository).save(approval);
    }

    @Test
    void finalApprovalStepAppliesMaintenanceBudgetApprovalHandler() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("MAINTENANCE_BUDGET");
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(documentId);
        budget.setStatus(BudgetStatus.DRAFT);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceBudgetRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(budget));
        when(maintenanceBudgetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.APPROVE);
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.APPROVED);
    }

    @Test
    void rejectionFinalizationAppliesProcurementRejectionCallback() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, documentId);
        approval.setDocumentType("PROCUREMENT_REQUEST");
        ProcurementRequest procurementRequest = procurementRequest(documentId, ProcurementRequestStatus.SUBMITTED);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(documentId)).thenReturn(Optional.of(procurementRequest));
        when(procurementRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reject(approvalId, new DecisionRequest(approverId, "missing invoice"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(procurementRequest.getStatus()).isEqualTo(ProcurementRequestStatus.REJECTED);
        assertThat(procurementRequest.getRejectionReason()).isEqualTo("missing invoice");
    }

    @Test
    void maintenanceDueEventApprovalPreservesCreateTaskActionAndRunsHandler() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, eventId);
        approval.setDocumentType("MAINTENANCE_DUE_EVENT");
        approval.setActionType(ApprovalActionType.CREATE_TASK);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceAutomationService.finalizeDueEventApproval(eventId, ApprovalActionType.CREATE_TASK, approverId))
                .thenReturn("{\"status\":\"TASK_CREATED\"}");

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.CREATE_TASK);
        assertThat(approval.getResultJson()).contains("TASK_CREATED");
        assertThat(approval.isExecuted()).isTrue();
        verify(maintenanceAutomationService).finalizeDueEventApproval(eventId, ApprovalActionType.CREATE_TASK, approverId);
    }

    @Test
    void maintenanceDueEventApprovalPreservesCreateWorkOrderActionAndRunsHandler() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, eventId);
        approval.setDocumentType("MAINTENANCE_DUE_EVENT");
        approval.setActionType(ApprovalActionType.CREATE_WORK_ORDER);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceAutomationService.finalizeDueEventApproval(eventId, ApprovalActionType.CREATE_WORK_ORDER, approverId))
                .thenReturn("{\"status\":\"WORK_ORDER_CREATED\"}");

        service.approve(approvalId, new DecisionRequest(approverId, "ok"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.CREATE_WORK_ORDER);
        assertThat(approval.getResultJson()).contains("WORK_ORDER_CREATED");
        verify(maintenanceAutomationService).finalizeDueEventApproval(eventId, ApprovalActionType.CREATE_WORK_ORDER, approverId);
    }

    @Test
    void maintenanceDueEventRejectionCancelsEventWithoutFinalCreation() {
        UUID approvalId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, UUID.randomUUID(), approverId, eventId);
        approval.setDocumentType("MAINTENANCE_DUE_EVENT");
        approval.setActionType(ApprovalActionType.CREATE_TASK);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceAutomationService.rejectDueEventApproval(eventId, "no"))
                .thenReturn("{\"status\":\"CANCELLED\"}");

        service.reject(approvalId, new DecisionRequest(approverId, "no"));

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.CREATE_TASK);
        assertThat(approval.getResultJson()).contains("CANCELLED");
        verify(maintenanceAutomationService).rejectDueEventApproval(eventId, "no");
        verify(maintenanceAutomationService, never()).finalizeDueEventApproval(any(), any(), any());
    }

    @Test
    void maintenanceDueEventApprovalCancelCancelsEventWithoutFinalCreation() {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ApprovalRequest approval = approval(approvalId, requesterId, approverId, eventId);
        approval.setDocumentType("MAINTENANCE_DUE_EVENT");
        approval.setActionType(ApprovalActionType.CREATE_WORK_ORDER);
        when(requestRepository.findByIdAndIsDeletedFalse(approvalId)).thenReturn(Optional.of(approval));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceAutomationService.rejectDueEventApproval(eventId, null))
                .thenReturn("{\"status\":\"CANCELLED\"}");

        service.cancel(approvalId);

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(approval.getActionType()).isEqualTo(ApprovalActionType.CREATE_WORK_ORDER);
        assertThat(approval.getResultJson()).contains("CANCELLED");
        verify(maintenanceAutomationService).rejectDueEventApproval(eventId, null);
        verify(maintenanceAutomationService, never()).finalizeDueEventApproval(any(), any(), any());
    }

    private ApprovalRequest approval(UUID id, UUID requesterId, UUID approverId, UUID documentId) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setId(id);
        approval.setDocumentType("WORK_ORDER");
        approval.setDocumentId(documentId);
        approval.setTitle("Approve work order");
        approval.setRequesterId(requesterId);
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);

        ApprovalStep step = new ApprovalStep();
        step.setId(UUID.randomUUID());
        step.setRequest(approval);
        step.setStepNumber(1);
        step.setApproverId(approverId);
        step.setDecision(ApprovalDecision.PENDING);
        approval.getSteps().add(step);
        return approval;
    }

    private CreateApprovalRequest createRequest(UUID requesterId, UUID approverId, UUID documentId) {
        return new CreateApprovalRequest(
                "WORK_ORDER",
                documentId,
                "Approve work order",
                requesterId,
                "Approval request",
                List.of(new CreateApprovalRequest.StepInput(approverId, "CHIEF_MECHANIC"))
        );
    }

    private ScopeAccessService scopeAccessService(UUID userId, Optional<UUID> employeeId, boolean scopeAdmin) {
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(userId);
        when(scopeAccessService.currentEmployeeId()).thenReturn(employeeId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(scopeAdmin);
        return scopeAccessService;
    }

    private ApprovalScopeService scope(ScopeAccessService scopeAccessService) {
        return scope(scopeAccessService, mock(WorkOrderRepository.class));
    }

    private ApprovalScopeService scope(ScopeAccessService scopeAccessService, WorkOrderRepository workOrderRepository) {
        return new ApprovalScopeService(
                scopeAccessService,
                mock(PprPlanRepository.class),
                mock(PprTaskRepository.class),
                mock(RepairRequestRepository.class),
                workOrderRepository,
                mock(ProcurementRequestRepository.class),
                mock(MaintenanceBudgetRepository.class),
                mock(ActualCostRepository.class),
                mock(FinanceScopeService.class)
        );
    }

    private WorkOrder workOrder(UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        return workOrder;
    }

    private ProcurementRequest procurementRequest(UUID id, ProcurementRequestStatus status) {
        ProcurementRequest request = new ProcurementRequest();
        request.setId(id);
        request.setStatus(status);
        request.setNumber("PR-2026-0001");
        request.setTitle("Procurement");
        return request;
    }
}
