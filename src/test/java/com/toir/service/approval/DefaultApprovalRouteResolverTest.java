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
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository);
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
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository);
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
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("REPAIR_CAMPAIGN_APPROVE");
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setActionType(ApprovalActionType.APPROVE);

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(7);
        assertThat(steps).extracting(CreateApprovalRequest.StepInput::approverRole)
                .containsExactlyElementsOf(com.toir.service.repair.RepairCampaignApprovalPolicy.DISCIPLINE_ROLES);
    }

    @Test
    void fallsBackToDomainPermissionWhenTemplateHasNoSteps() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository);
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
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository);
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
}
