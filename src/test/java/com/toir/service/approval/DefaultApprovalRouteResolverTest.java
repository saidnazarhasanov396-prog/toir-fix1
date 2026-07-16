package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovalTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultApprovalRouteResolverTest {

    @Test
    void usesExactlyTheConfiguredApproverRole() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("USTA");
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(template));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().approverId()).isNull();
        assertThat(steps.getFirst().approverRole()).isEqualTo("USTA");
    }

    @Test
    void fallsBackToLegacyTargetTemplateButKeepsItsConfiguredRole() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("USTA");
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        request.setActionType(ApprovalActionType.UPDATE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.UPDATE
        )).thenReturn(Optional.empty());
        when(templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER
        )).thenReturn(Optional.of(template));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().approverId()).isNull();
        assertThat(steps.getFirst().approverRole()).isEqualTo("USTA");
    }

    @Test
    void repairCampaignApproveAlwaysUsesCanonicalSevenDisciplineRoute() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("REPAIR_CAMPAIGN_APPROVE");
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setActionType(ApprovalActionType.APPROVE);
        template.setActionType(ApprovalActionType.APPROVE);
        int order = 1;
        for (String role : com.toir.service.repair.RepairCampaignApprovalRouteValidator.REQUIRED_DISCIPLINE_ROLES) {
            com.toir.entity.ApprovalTemplateStep step = new com.toir.entity.ApprovalTemplateStep();
            step.setTemplate(template);
            step.setStepOrder(order++);
            step.setApproverRole(role);
            template.getSteps().add(step);
        }
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(template));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(7);
        assertThat(steps).extracting(CreateApprovalRequest.StepInput::approverRole)
                .containsExactlyElementsOf(com.toir.service.repair.RepairCampaignApprovalPolicy.DISCIPLINE_ROLES);
    }

    @Test
    void fallsBackToDomainPermissionWhenTemplateHasNoSteps() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.empty());
        when(templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER
        )).thenReturn(Optional.empty());

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().approverId()).isNull();
        assertThat(steps.getFirst().approverRole()).isEqualTo("WORK_ORDER_APPROVE");
    }

    @Test
    void emptyTemplateFallsBackToDomainPermissionStep() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setRoutePolicy(ApprovalRoutePolicy.DEPARTMENT_HEAD);
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(template));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().approverRole()).isEqualTo("WORK_ORDER_APPROVE");
    }

    @Test
    void repairCampaignApproveDoesNotSynthesizeRouteWhenConfigurationIsMissing() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setActionType(ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.empty());

        assertThat(resolver.resolveRoute(request)).isEmpty();
    }

    @Test
    void repairCampaignApproveRejectsConfiguredOneStepSystemAdminRoute() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setActionType(ApprovalActionType.APPROVE);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        template.setActionType(ApprovalActionType.APPROVE);
        com.toir.entity.ApprovalTemplateStep step = new com.toir.entity.ApprovalTemplateStep();
        step.setTemplate(template);
        step.setStepOrder(1);
        step.setApproverRole("SYSTEM_ADMIN");
        template.getSteps().add(step);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(template));

        assertThat(resolver.resolveRoute(request)).isEmpty();
    }


    @Test
    void plannedShutdownApproveRequiresCanonicalProductionThenHseTemplate() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        template.setActionType(ApprovalActionType.APPROVE);
        int order = 1;
        for (String role : com.toir.service.plannedshutdown.PlannedShutdownApprovalRouteValidator.REQUIRED_APPROVER_ROLES) {
            com.toir.entity.ApprovalTemplateStep step = new com.toir.entity.ApprovalTemplateStep();
            step.setTemplate(template);
            step.setStepOrder(order++);
            step.setApproverRole(role);
            template.getSteps().add(step);
        }
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        request.setActionType(ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(template));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(2);
        assertThat(steps).extracting(CreateApprovalRequest.StepInput::approverRole)
                .containsExactlyElementsOf(com.toir.service.plannedshutdown.PlannedShutdownApprovalRouteValidator.REQUIRED_APPROVER_ROLES);
    }

    @Test
    void plannedShutdownApproveDoesNotUseGenericFallbackWhenTemplateIsMissingOrInvalid() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(templateRepository);
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        request.setActionType(ApprovalActionType.APPROVE);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.empty());

        assertThat(resolver.resolveRoute(request)).isEmpty();

        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        template.setActionType(ApprovalActionType.APPROVE);
        com.toir.entity.ApprovalTemplateStep step = new com.toir.entity.ApprovalTemplateStep();
        step.setTemplate(template);
        step.setStepOrder(1);
        step.setApproverRole("PLANNED_SHUTDOWN_APPROVE");
        template.getSteps().add(step);
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(template));

        assertThat(resolver.resolveRoute(request)).isEmpty();
    }

    private static DefaultApprovalRouteResolver resolver(ApprovalTemplateRepository templateRepository) {
        return new DefaultApprovalRouteResolver(
                templateRepository,
                new com.toir.service.repair.RepairCampaignApprovalRouteValidator(),
                new com.toir.service.plannedshutdown.PlannedShutdownApprovalRouteValidator());
    }
}
