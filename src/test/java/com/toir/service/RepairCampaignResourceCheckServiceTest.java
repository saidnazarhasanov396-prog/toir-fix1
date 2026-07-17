package com.toir.service;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.approval.LifecycleApprovalRoutePolicy;
import com.toir.service.approval.LifecycleApprovalStartPlan;
import com.toir.service.repair.RepairCampaignApprovalPolicy;
import com.toir.service.repair.RepairCampaignMutationImpactService;
import com.toir.service.repair.RepairCampaignService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairCampaignResourceCheckServiceTest {

    @Mock private RepairCampaignRepository repository;
    @Mock private RepairCampaignStageRepository stageRepository;
    @Mock private RepairCampaignDepartmentRepository campaignDepartmentRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private ContractorWorkRepository contractorWorkRepository;
    @Mock private ActualCostRepository actualCostRepository;
    @Mock private MaintenanceBudgetRepository maintenanceBudgetRepository;
    @Mock private BudgetLineRepository budgetLineRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RepairAcceptanceRepository repairAcceptanceRepository;
    @Mock private WorkOrderService workOrderService;
    @Mock private AuditBuilderService auditBuilderService;
    @Mock private RepairCampaignApprovalPolicy approvalPolicy;
    @Mock private ScopeAccessService scopeAccessService;
    @Mock private ObjectProvider<ApprovalService> approvalServiceProvider;
    @Mock private ApprovalService approvalService;
    @Mock private RepairCampaignMutationImpactService mutationImpactService;

    @InjectMocks private RepairCampaignService service;

    @Test
    void startResourceCheckTransitionsDraftToResourceCheck() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.DRAFT, 0L, 0L);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(repository.saveAndFlush(campaign)).thenReturn(campaign);

        RepairCampaignDto result = service.startResourceCheck(campaignId, 0L, 0L);

        assertThat(result.status()).isEqualTo(RepairCampaignStatus.RESOURCE_CHECK);
        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.RESOURCE_CHECK);
        assertThat(campaign.getApprovalScopeVersion()).isNull();
        assertThat(campaign.getApprovalScopeHash()).isNull();
        verify(scopeAccessService).assertCanAccessDepartment(campaign.getDepartmentId());
        verify(repository).findLockedByIdAndIsDeletedFalse(campaignId);
        verify(repository).saveAndFlush(campaign);
        verifyNoInteractions(approvalServiceProvider);
    }

    @Test
    void startResourceCheckTransitionsScopeFormationToResourceCheck() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.SCOPE_FORMATION, 2L, 5L);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(repository.saveAndFlush(campaign)).thenReturn(campaign);

        RepairCampaignDto result = service.startResourceCheck(campaignId, 2L, 5L);

        assertThat(result.status()).isEqualTo(RepairCampaignStatus.RESOURCE_CHECK);
        assertThat(campaign.getScopeVersion()).isEqualTo(5L);
        verify(repository).saveAndFlush(campaign);
    }

    @ParameterizedTest
    @EnumSource(value = RepairCampaignStatus.class, names = {
            "RESOURCE_CHECK", "PENDING_APPROVAL", "APPROVED", "PREPARATION",
            "IN_PROGRESS", "SUSPENDED", "COMPLETED", "CLOSING", "CLOSED", "CANCELLED"
    })
    void startResourceCheckRejectsInvalidStatuses(RepairCampaignStatus status) {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, status, 1L, 2L);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.startResourceCheck(campaignId, 1L, 2L))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("REPAIR_CAMPAIGN_NOT_READY_FOR_RESOURCE_CHECK");
                });

        assertThat(campaign.getStatus()).isEqualTo(status);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void startResourceCheckRejectsStaleVersion() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.DRAFT, 3L, 0L);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.startResourceCheck(campaignId, 2L, 0L))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("version conflict");
                });

        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.DRAFT);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void startResourceCheckRejectsStaleScopeVersion() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.DRAFT, 3L, 4L);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.startResourceCheck(campaignId, 3L, 3L))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("REPAIR_CAMPAIGN_STALE_SCOPE_VERSION");
                });

        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.DRAFT);
        assertThat(campaign.getApprovalScopeVersion()).isNull();
        assertThat(campaign.getApprovalScopeHash()).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void startResourceCheckEnforcesDepartmentAccess() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.DRAFT, 0L, 0L);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        doThrow(new AccessDeniedException("Access denied by repair campaign scope"))
                .when(scopeAccessService).assertCanAccessDepartment(campaign.getDepartmentId());

        assertThatThrownBy(() -> service.startResourceCheck(campaignId, 0L, 0L))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.DRAFT);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void startResourceCheckDoesNotCreateApprovalRequest() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.DRAFT, 0L, 0L);
        campaign.setApprovalScopeVersion(9L);
        campaign.setApprovalScopeHash("b".repeat(64));
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(repository.saveAndFlush(campaign)).thenReturn(campaign);

        service.startResourceCheck(campaignId, 0L, 0L);

        assertThat(campaign.getApprovalScopeVersion()).isEqualTo(9L);
        assertThat(campaign.getApprovalScopeHash()).isEqualTo("b".repeat(64));
        verifyNoInteractions(approvalServiceProvider);
    }

    @Test
    void requestApprovalSucceedsAfterResourceCheckTransition() {
        UUID campaignId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, RepairCampaignStatus.DRAFT, 0L, 0L);
        campaign.setCode("RCMP-2026-001");
        campaign.setName("Annual overhaul");
        LifecycleApprovalStartPlan plan = new LifecycleApprovalStartPlan(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                campaignId,
                ApprovalActionType.APPROVE,
                null,
                List.of(new CreateApprovalRequest.StepInput(null, "APPROVER")),
                LifecycleApprovalRoutePolicy.Reason.VALID);
        when(repository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(repository.saveAndFlush(campaign)).thenReturn(campaign);
        when(approvalServiceProvider.getObject()).thenReturn(approvalService);
        when(approvalService.planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                campaignId,
                ApprovalActionType.APPROVE,
                false,
                null)).thenReturn(plan);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(requesterId);
        doAnswer(invocation -> {
            RepairCampaign c = invocation.getArgument(0);
            c.setApprovalScopeVersion(c.getScopeVersion());
            c.setApprovalScopeHash("a".repeat(64));
            c.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
            return null;
        }).when(approvalPolicy).prepareRequest(campaign, 0L);

        service.startResourceCheck(campaignId, 0L, 0L);
        RepairCampaignDto result = service.requestApproval(campaignId, 0L, 0L, null);

        assertThat(result.status()).isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);
        assertThat(result.approvalScopeVersion()).isEqualTo(0L);
        assertThat(result.approvalScopeHash()).isEqualTo("a".repeat(64));
        InOrder inOrder = inOrder(approvalService, repository);
        inOrder.verify(approvalService).planLifecycleApproval(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                campaignId,
                ApprovalActionType.APPROVE,
                false,
                null);
        inOrder.verify(repository).saveAndFlush(campaign);
        inOrder.verify(approvalService).materializeLifecycleApproval(
                eq(plan), eq(requesterId), anyString(), any(), anyString());
        verify(repository, times(2)).saveAndFlush(campaign);
    }

    private RepairCampaign campaign(UUID id, RepairCampaignStatus status, Long version, Long scopeVersion) {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(id);
        campaign.setDepartmentId(UUID.randomUUID());
        campaign.setStatus(status);
        campaign.setVersion(version);
        campaign.setScopeVersion(scopeVersion);
        campaign.setStartDate(LocalDate.of(2026, 1, 1));
        campaign.setEndDate(LocalDate.of(2026, 12, 31));
        campaign.setStages(new java.util.ArrayList<>());
        return campaign;
    }
}
