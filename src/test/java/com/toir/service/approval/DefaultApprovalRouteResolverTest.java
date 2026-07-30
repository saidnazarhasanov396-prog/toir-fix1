package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.UserStatus;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.MULTIPLE_ACTIVE_TEMPLATES;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.NO_ACTIVE_TEMPLATE;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.NONCONTIGUOUS_ORDER;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultApprovalRouteResolverTest {

    private static final UUID FIRST_ROLE_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID SECOND_ROLE_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Test
    void noExactTemplateReturnsNoActiveTemplate() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of());

        LifecycleRouteResolution result = resolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE);

        assertThat(result.resolved()).isFalse();
        assertThat(result.reason()).isEqualTo(NO_ACTIVE_TEMPLATE);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void multipleExactTemplatesReturnAmbiguousWithoutChoosingLatest() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate newest = mock(ApprovalTemplate.class);
        ApprovalTemplate older = mock(ApprovalTemplate.class);
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(newest, older));

        LifecycleRouteResolution result = resolver.resolveLifecycleRoute(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE);

        assertThat(result.resolved()).isFalse();
        assertThat(result.reason()).isEqualTo(MULTIPLE_ACTIVE_TEMPLATES);
        assertThat(result.steps()).isEmpty();
        verify(newest, never()).getSteps();
        verify(older, never()).getSteps();
    }

    @Test
    void oneTemplateFreezesOrderedRoleAndExplicitAssignments() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        UUID explicitApprover = UUID.randomUUID();
        ApprovalTemplate template = lifecycleTemplate(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                roleStep(2, "  SECOND_APPROVER  "),
                explicitStep(1, explicitApprover));
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(template));

        LifecycleRouteResolution result = resolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE);

        assertThat(result.resolved()).isTrue();
        assertThat(result.reason()).isEqualTo(VALID);
        assertThat(result.steps()).containsExactly(
                new CreateApprovalRequest.StepInput(explicitApprover, null),
                new CreateApprovalRequest.StepInput(null, "SECOND_APPROVER"));
        assertThat(result.steps()).allSatisfy(step ->
                assertThat((step.approverId() == null) ^ (step.approverRole() == null)).isTrue());
        template.getSteps().getFirst().setApproverRole("CHANGED_AFTER_RESOLUTION");
        assertThat(result.steps().get(1).approverRole()).isEqualTo("SECOND_APPROVER");
        assertThatThrownBy(() -> result.steps().add(
                new CreateApprovalRequest.StepInput(null, "THIRD_APPROVER")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void malformedTemplateReturnsPolicyReason() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = lifecycleTemplate(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                roleStep(1, "FIRST"),
                roleStep(3, "THIRD"));
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(template));

        LifecycleRouteResolution result = resolver.resolveLifecycleRoute(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE);

        assertThat(result.resolved()).isFalse();
        assertThat(result.reason()).isEqualTo(NONCONTIGUOUS_ORDER);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void oneStepSystemAdminLifecycleTemplateIsAValidFrozenRoute() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = lifecycleTemplate(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                roleStep(1, "SYSTEM_ADMIN"));
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(template));

        LifecycleRouteResolution result = resolver.resolveLifecycleRoute(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE);

        assertThat(result.resolved()).isTrue();
        assertThat(result.steps()).containsExactly(
                new CreateApprovalRequest.StepInput(null, "SYSTEM_ADMIN"));
    }

    @Test
    void arbitraryRepeatedRoleLifecycleTemplateIsAValidFrozenRoute() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = lifecycleTemplate(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                roleStep(4, "REVIEWER"),
                roleStep(2, "REVIEWER"),
                roleStep(1, "REVIEWER"),
                roleStep(3, "REVIEWER"));
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(template));

        LifecycleRouteResolution result = resolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE);

        assertThat(result.resolved()).isTrue();
        assertThat(result.steps()).hasSize(4)
                .allSatisfy(step -> assertThat(step.approverRole()).isEqualTo("REVIEWER"));
    }

    @Test
    void lifecycleResolutionNeverCallsTargetOnlyOrPermissionFallback() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = request(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE);
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of());

        assertThat(resolver.resolveRoute(request)).isEmpty();

        verify(templateRepository, never())
                .findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                        ApprovalTargetType.REPAIR_CAMPAIGN);
        verify(templateRepository, never())
                .findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                        ApprovalTargetType.REPAIR_CAMPAIGN,
                        ApprovalActionType.APPROVE);
    }

    @Test
    void unrelatedTargetKeepsExistingGenericResolution() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("USTA");
        ApprovalRequest request = request(ApprovalTargetType.WORK_ORDER, ApprovalActionType.UPDATE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.UPDATE
        )).thenReturn(Optional.empty());
        when(templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER
        )).thenReturn(Optional.of(template));

        assertThat(resolver.resolveRoute(request)).containsExactly(
                new CreateApprovalRequest.StepInput(null, "USTA"));
    }

    @Test
    void pprApprovalRequiresOneExactActiveConfiguredTemplate() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = request(
                ApprovalTargetType.PPR_PLAN,
                ApprovalActionType.APPROVE);
        when(templateRepository
                .findByCodeAndActiveTrueAndIsDeletedFalse(
                        "PPR_PLAN_APPROVAL"))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolveRouteSnapshot(request).steps()).isEmpty();
        verify(templateRepository, never())
                .findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                        ApprovalTargetType.PPR_PLAN);
    }

    @Test
    void pprApprovalRejectsLegacyTopLevelApproverWithoutConfiguredStep() {
        ApprovalTemplateRepository templateRepository =
                mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setCode("PPR_PLAN_APPROVAL");
        template.setTargetType(ApprovalTargetType.PPR_PLAN);
        template.setActionType(ApprovalActionType.APPROVE);
        template.setActive(true);
        template.setApproverId(UUID.randomUUID());
        when(templateRepository.findByCodeAndActiveTrueAndIsDeletedFalse(
                "PPR_PLAN_APPROVAL")).thenReturn(Optional.of(template));

        assertThat(resolver.resolveRouteSnapshot(request(
                ApprovalTargetType.PPR_PLAN,
                ApprovalActionType.APPROVE)).steps()).isEmpty();
    }

    @Test
    void pprApprovalFreezesTemplateConfiguredChiefEngineerActorWithoutPermissionFallback() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        UUID chiefEngineer = UUID.randomUUID();
        ApprovalTemplate template = new ApprovalTemplate();
        template.setId(UUID.randomUUID());
        template.setCode("PPR_PLAN_APPROVAL");
        template.setTargetType(ApprovalTargetType.PPR_PLAN);
        template.setActionType(ApprovalActionType.APPROVE);
        template.setActive(true);
        template.setVersion(3L);
        template.getSteps().add(explicitStep(1, chiefEngineer));
        when(templateRepository
                .findByCodeAndActiveTrueAndIsDeletedFalse(
                        "PPR_PLAN_APPROVAL"))
                .thenReturn(Optional.of(template));

        ApprovalRouteSnapshot route = resolver.resolveRouteSnapshot(request(
                ApprovalTargetType.PPR_PLAN,
                ApprovalActionType.APPROVE));

        assertThat(route.templateId()).isEqualTo(template.getId());
        assertThat(route.steps()).containsExactly(
                new CreateApprovalRequest.StepInput(chiefEngineer, null));
    }

    @Test
    void genericParallelRouteFreezesFlowAndTemplateProvenance() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UserRepository userRepository = mock(UserRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository, userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(first, "ANY"), user(second, "ANY")));
        UUID templateId = UUID.randomUUID();
        ApprovalTemplate template = new ApprovalTemplate();
        ReflectionTestUtils.setField(template, "id", templateId);
        template.setVersion(7L);
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setActionType(ApprovalActionType.APPROVE);
        template.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        template.getSteps().add(explicitStep(1, first));
        template.getSteps().add(explicitStep(2, second));
        ApprovalRequest request = request(ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE)).thenReturn(Optional.of(template));

        ApprovalRouteSnapshot snapshot = resolver.resolveRouteSnapshot(request);

        assertThat(snapshot.flowType()).isEqualTo(ApprovalFlowType.PARALLEL_ALL);
        assertThat(snapshot.templateId()).isEqualTo(templateId);
        assertThat(snapshot.templateVersion()).isEqualTo(7L);
        assertThat(snapshot.steps()).extracting(CreateApprovalRequest.StepInput::approverId)
                .containsExactly(first, second);
    }

    @Test
    void genericParallelRoleIsExpandedBeforeSnapshotIsReturned() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository, userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(FIRST_ROLE_MEMBER, "WORK_ORDER_APPROVER"),
                user(SECOND_ROLE_MEMBER, "WORK_ORDER_APPROVER")));
        ApprovalTemplate template = parallelTemplate(roleStep(1, "WORK_ORDER_APPROVER"));
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setActionType(ApprovalActionType.APPROVE);
        ApprovalRequest request = request(ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE)).thenReturn(Optional.of(template));

        ApprovalRouteSnapshot snapshot = resolver.resolveRouteSnapshot(request);

        assertThat(snapshot.flowType()).isEqualTo(ApprovalFlowType.PARALLEL_ALL);
        assertThat(snapshot.steps()).extracting(CreateApprovalRequest.StepInput::approverId)
                .containsExactly(FIRST_ROLE_MEMBER, SECOND_ROLE_MEMBER);
        assertThat(snapshot.steps()).extracting(CreateApprovalRequest.StepInput::approverRole)
                .containsOnlyNulls();
    }

    @Test
    void genericParallelSnapshotStaysFrozenWhenRoleMembershipChanges() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository, userRepository);
        ApprovalTemplate template = parallelTemplate(roleStep(1, "WORK_ORDER_APPROVER"));
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setActionType(ApprovalActionType.APPROVE);
        ApprovalRequest request = request(ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE)).thenReturn(Optional.of(template));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(
                List.of(user(FIRST_ROLE_MEMBER, "WORK_ORDER_APPROVER")),
                List.of(user(SECOND_ROLE_MEMBER, "WORK_ORDER_APPROVER")));

        ApprovalRouteSnapshot firstSnapshot = resolver.resolveRouteSnapshot(request);
        ApprovalRouteSnapshot secondSnapshot = resolver.resolveRouteSnapshot(request);

        assertThat(firstSnapshot.steps()).containsExactly(
                new CreateApprovalRequest.StepInput(FIRST_ROLE_MEMBER, null));
        assertThat(secondSnapshot.steps()).containsExactly(
                new CreateApprovalRequest.StepInput(SECOND_ROLE_MEMBER, null));
        assertThatThrownBy(() -> firstSnapshot.steps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lifecycleParallelRoleIsExpandedBeforeMaterialization() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository, userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(FIRST_ROLE_MEMBER, "CAMPAIGN_APPROVER"),
                user(SECOND_ROLE_MEMBER, "CAMPAIGN_APPROVER")));
        ApprovalTemplate template = lifecycleTemplate(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                roleStep(1, "CAMPAIGN_APPROVER"));
        template.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(template));

        LifecycleRouteResolution resolution = resolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE);

        assertThat(resolution.steps()).extracting(CreateApprovalRequest.StepInput::approverId)
                .containsExactly(FIRST_ROLE_MEMBER, SECOND_ROLE_MEMBER);
        assertThat(resolution.steps()).allSatisfy(step -> {
            assertThat(step.approverId()).isNotNull();
            assertThat(step.approverRole()).isNull();
        });
    }

    @Test
    void unrelatedTargetKeepsDomainPermissionFallback() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = request(ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.empty());
        when(templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER
        )).thenReturn(Optional.empty());

        assertThat(resolver.resolveRoute(request)).containsExactly(
                new CreateApprovalRequest.StepInput(null, "WORK_ORDER_APPROVE"));
    }

    private static DefaultApprovalRouteResolver resolver(ApprovalTemplateRepository templateRepository) {
        return resolver(templateRepository, mock(UserRepository.class));
    }

    private static DefaultApprovalRouteResolver resolver(ApprovalTemplateRepository templateRepository,
                                                         UserRepository userRepository) {
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByCodeAndIsDeletedFalse(anyString()))
                .thenAnswer(invocation -> Optional.of(
                        Role.builder().code(invocation.getArgument(0)).build()));
        return new DefaultApprovalRouteResolver(
                templateRepository,
                new LifecycleApprovalRoutePolicy(),
                new ParallelApprovalAssigneeResolver(userRepository, roleRepository));
    }

    private static ApprovalRequest request(ApprovalTargetType targetType,
                                           ApprovalActionType actionType) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(targetType);
        request.setActionType(actionType);
        return request;
    }

    private static ApprovalTemplate lifecycleTemplate(ApprovalTargetType targetType,
                                                      ApprovalTemplateStep... steps) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(targetType);
        template.setActionType(ApprovalActionType.APPROVE);
        for (ApprovalTemplateStep step : steps) {
            step.setTemplate(template);
            template.getSteps().add(step);
        }
        return template;
    }

    private static ApprovalTemplate parallelTemplate(ApprovalTemplateStep... steps) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        for (ApprovalTemplateStep step : steps) {
            step.setTemplate(template);
            template.getSteps().add(step);
        }
        return template;
    }

    private static User user(UUID id, String roleCode) {
        return User.builder()
                .id(id)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .primaryRole(Role.builder().code(roleCode).build())
                .roles(Set.of())
                .build();
    }

    private static ApprovalTemplateStep roleStep(int order, String role) {
        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setStepOrder(order);
        step.setApproverRole(role);
        return step;
    }

    private static ApprovalTemplateStep explicitStep(int order, UUID approverId) {
        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setStepOrder(order);
        step.setApproverId(approverId);
        return step;
    }
}
