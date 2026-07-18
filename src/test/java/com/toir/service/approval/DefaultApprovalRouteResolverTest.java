package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovalTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.MULTIPLE_ACTIVE_TEMPLATES;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.NO_ACTIVE_TEMPLATE;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.NONCONTIGUOUS_ORDER;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultApprovalRouteResolverTest {

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
        return new DefaultApprovalRouteResolver(
                templateRepository,
                new LifecycleApprovalRoutePolicy());
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
