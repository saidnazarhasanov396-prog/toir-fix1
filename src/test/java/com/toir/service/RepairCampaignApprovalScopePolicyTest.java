package com.toir.service;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.security.SecurityAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepairCampaignApprovalScopePolicyTest {

    private static final List<String> ROLES = List.of(
            "REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER", "REPAIR_CAMPAIGN_PRODUCTION_APPROVER",
            "REPAIR_CAMPAIGN_WAREHOUSE_APPROVER", "REPAIR_CAMPAIGN_PROCUREMENT_APPROVER",
            "REPAIR_CAMPAIGN_FINANCE_APPROVER", "REPAIR_CAMPAIGN_HSE_APPROVER",
            "REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER");

    @Test
    void requesterCannotApproveAnyDiscipline() {
        UUID requester = UUID.randomUUID(), department = UUID.randomUUID();
        Fixture fixture = fixture(requester, department, true);
        for (String role : ROLES) {
            ApprovalRequest approval = approval(requester, step(1, role, requester, ApprovalDecision.PENDING));
            assertThatThrownBy(() -> fixture.service.assertCanDecideApproval(approval, approval.getSteps().getFirst()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("own request");
        }
    }

    @Test
    void sameActorCannotApproveTwoDisciplinesIncludingWhenDelegated() {
        UUID actor = UUID.randomUUID(), requester = UUID.randomUUID(), department = UUID.randomUUID();
        Fixture fixture = fixture(actor, department, true);
        ApprovalStep prior = step(1, ROLES.get(0), UUID.randomUUID(), ApprovalDecision.APPROVED);
        prior.setDecidedById(actor);
        ApprovalStep current = step(2, ROLES.get(1), UUID.randomUUID(), ApprovalDecision.PENDING);
        ApprovalRequest approval = approval(requester, prior, current);
        approval.setCurrentStep(2);

        assertThatThrownBy(() -> fixture.service.assertCanDecideApproval(
                approval, current, current.getApproverId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("distinct actor");
    }

    @Test
    void roleMatchingIsExactAndRejectsSubstringLookalike() {
        UUID actor = UUID.randomUUID(), requester = UUID.randomUUID(), department = UUID.randomUUID();
        Fixture fixture = fixture(actor, department, true);
        User user = new User();
        user.setId(actor);
        user.setStatus(com.toir.enums.UserStatus.ACTIVE);
        Role lookalike = new Role();
        lookalike.setCode("X_" + ROLES.get(0) + "_EXTRA");
        user.setPrimaryRole(lookalike);
        when(fixture.users.findByIdAndIsDeletedFalse(actor)).thenReturn(Optional.of(user));
        ApprovalRequest approval = approval(requester, step(1, ROLES.get(0), null, ApprovalDecision.PENDING));

        assertThatThrownBy(() -> fixture.service.assertCanDecideApproval(approval, approval.getSteps().getFirst()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void crossDepartmentReadDecisionAndDelegationAreDenied() {
        UUID actor = UUID.randomUUID(), requester = UUID.randomUUID(), department = UUID.randomUUID();
        Fixture fixture = fixture(actor, department, false);
        ApprovalStep current = step(1, ROLES.get(0), actor, ApprovalDecision.PENDING);
        ApprovalRequest approval = approval(requester, current);

        assertThatThrownBy(() -> fixture.service.assertCanReadApproval(approval)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fixture.service.assertCanDecideApproval(approval, current)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fixture.service.assertCanDecideApproval(approval, current, actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sevenDistinctActorsCanActOnlyOnTheirOrderedCurrentDiscipline() {
        UUID requester = UUID.randomUUID(), department = UUID.randomUUID();
        List<ApprovalStep> steps = new ArrayList<>();
        List<UUID> actors = ROLES.stream().map(role -> UUID.randomUUID()).toList();
        for (int index = 0; index < ROLES.size(); index++) {
            steps.add(step(index + 1, ROLES.get(index), actors.get(index), ApprovalDecision.PENDING));
        }
        ApprovalRequest approval = approval(requester, steps.toArray(ApprovalStep[]::new));
        Fixture fixture = fixture(actors.getFirst(), department, true);
        for (int index = 0; index < steps.size(); index++) {
            when(fixture.scope.currentUserIdOrNull()).thenReturn(actors.get(index));
            approval.setCurrentStep(index + 1);
            ApprovalStep current = steps.get(index);
            assertThatNoException().isThrownBy(() -> fixture.service.assertCanDecideApproval(approval, current));
            current.setDecision(ApprovalDecision.APPROVED);
            current.setDecidedById(actors.get(index));
        }
    }

    private Fixture fixture(UUID actor, UUID department, boolean departmentAccess) {
        ScopeAccessService scope = mock(ScopeAccessService.class);
        when(scope.currentUserIdOrNull()).thenReturn(actor);
        when(scope.currentEmployeeId()).thenReturn(Optional.empty());
        when(scope.canAccessDepartment(department)).thenReturn(departmentAccess);
        RepairCampaignRepository campaigns = mock(RepairCampaignRepository.class);
        RepairCampaign campaign = new RepairCampaign();
        campaign.setDepartmentId(department);
        when(campaigns.findByIdAndIsDeletedFalse(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(campaign));
        UserRepository users = mock(UserRepository.class);
        ApprovalScopeService service = new ApprovalScopeService(scope,
                mock(com.toir.repository.PprPlanRepository.class), mock(com.toir.repository.PprTaskRepository.class),
                mock(com.toir.repository.repair.RepairRequestRepository.class), mock(com.toir.repository.WorkOrderRepository.class),
                mock(com.toir.repository.ProcurementRequestRepository.class), mock(com.toir.repository.maintenance.MaintenanceBudgetRepository.class),
                mock(com.toir.repository.actualCost.ActualCostRepository.class), mock(FinanceScopeService.class), users,
                mock(com.toir.repository.equipment.EquipmentCommissioningActRepository.class),
                mock(com.toir.repository.PlannedShutdownRepository.class), campaigns, new SecurityAccessService());
        return new Fixture(service, scope, users);
    }

    private ApprovalRequest approval(UUID requester, ApprovalStep... steps) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setTargetId(UUID.randomUUID());
        request.setRequesterId(requester);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(1);
        request.setSteps(new ArrayList<>(List.of(steps)));
        request.getSteps().forEach(step -> step.setRequest(request));
        return request;
    }

    private ApprovalStep step(int number, String role, UUID approver, ApprovalDecision decision) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(number);
        step.setApproverRole(role);
        step.setApproverId(approver);
        step.setDecision(decision);
        return step;
    }

    private record Fixture(ApprovalScopeService service, ScopeAccessService scope, UserRepository users) {}
}
