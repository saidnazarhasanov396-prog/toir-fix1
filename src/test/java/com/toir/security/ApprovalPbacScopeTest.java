package com.toir.security;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.ApprovalScopeService;
import com.toir.service.ApprovalService;
import com.toir.service.FinanceScopeService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalPbacScopeTest {

    ApprovalRequestRepository requestRepository;
    AuditBuilderService auditBuilderService;
    ApprovalScopeService approvalScopeService;
    ApprovalService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(ApprovalRequestRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        approvalScopeService = mock(ApprovalScopeService.class);
        service = new ApprovalService(requestRepository, auditBuilderService, approvalScopeService);
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
        when(requestRepository.findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalStatus.PENDING))
                .thenReturn(List.of(allowed, denied));
        when(approvalScopeService.canReadApproval(allowed)).thenReturn(true);
        when(approvalScopeService.canReadApproval(denied)).thenReturn(false);

        var result = service.pending();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(allowed.getId());
    }

    @Test
    void createValidatesRequesterAndDocumentScope() {
        CreateApprovalRequest request = createRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(approvalScopeService).assertCanCreateApproval(request);
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
}
