package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalRuleServiceTest {

    private final ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApprovalRuleService service = new ApprovalRuleService(templateRepository, userRepository);

    @Test
    void returnsTemplateRulesWithRoleAndUserStepsAndCorrectCount() {
        UUID userId = UUID.randomUUID();
        ApprovalTemplate template = template(
                "WORK_ORDER_APPROVAL",
                "Work Order",
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        );
        template.getSteps().add(step(template, 2, userId, null));
        template.getSteps().add(step(template, 1, null, "DEPARTMENT_HEAD"));
        User user = User.builder().id(userId).fullName("Ali Valiyev").build();

        when(templateRepository.findAllRules()).thenReturn(List.of(template));
        when(userRepository.findAllByIdInAndIsDeletedFalse(java.util.Set.of(userId))).thenReturn(List.of(user));

        var rules = service.listRules();

        assertThat(rules).hasSize(1);
        assertThat(rules.getFirst().stepsCount()).isEqualTo(2);
        assertThat(rules.getFirst().steps()).extracting(step -> step.order()).containsExactly(1, 2);
        assertThat(rules.getFirst().steps().getFirst().approverId()).isNull();
        assertThat(rules.getFirst().steps().getFirst().approverRole()).isEqualTo("DEPARTMENT_HEAD");
        assertThat(rules.getFirst().steps().getFirst().approverType().name()).isEqualTo("ROLE");
        assertThat(rules.getFirst().steps().get(1).approverId()).isEqualTo(userId);
        assertThat(rules.getFirst().steps().get(1).approverName()).isEqualTo("Ali Valiyev");
        assertThat(rules.getFirst().steps().get(1).approverRole()).isNull();
        assertThat(rules.getFirst().steps().get(1).approverType().name()).isEqualTo("USER");
    }

    @Test
    void sortsRulesStablyByTargetActionAndCodeWithoutReadingRuntimeRequests() {
        ApprovalTemplate update = template("B_UPDATE", "Work Order Update", ApprovalTargetType.WORK_ORDER, ApprovalActionType.UPDATE);
        ApprovalTemplate approveB = template("B_APPROVE", "Work Order B", ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE);
        ApprovalTemplate approveA = template("A_APPROVE", "Work Order A", ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE);
        ApprovalTemplate budget = template("BUDGET", "Budget", ApprovalTargetType.BUDGET, ApprovalActionType.APPROVE);

        when(templateRepository.findAllRules()).thenReturn(List.of(update, approveB, budget, approveA));

        var rules = service.listRules();

        assertThat(rules).extracting(rule -> rule.documentName())
                .containsExactly("Budget", "Work Order A", "Work Order B", "Work Order Update");
        verifyNoInteractions(userRepository);
    }

    @Test
    void configuredRoleOnlyTemplateRemainsAOneStepRule() {
        ApprovalTemplate template = template(
                "REPAIR_REQUEST_APPROVAL",
                "Repair Request",
                ApprovalTargetType.REPAIR_REQUEST,
                ApprovalActionType.APPROVE
        );
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("USTA");
        when(templateRepository.findAllRules()).thenReturn(List.of(template));

        var rule = service.listRules().getFirst();

        assertThat(rule.stepsCount()).isOne();
        assertThat(rule.steps().getFirst().approverId()).isNull();
        assertThat(rule.steps().getFirst().approverRole()).isEqualTo("USTA");
    }

    @Test
    void routePolicyDoesNotInventRuleSteps() {
        ApprovalTemplate template = template(
                "WORK_ORDER_APPROVAL",
                "Work Order",
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        );
        template.setRoutePolicy(ApprovalRoutePolicy.SYSTEM_ADMIN);
        when(templateRepository.findAllRules()).thenReturn(List.of(template));

        var rule = service.listRules().getFirst();

        assertThat(rule.stepsCount()).isZero();
        assertThat(rule.steps()).isEmpty();
    }

    @Test
    void saveRulePersistsRoleOnlyTemplateAndReplacesExistingSteps() {
        ApprovalTemplate template = template(
                "WORK_ORDER_APPROVE",
                "Old Work Order",
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        );
        template.getSteps().add(step(template, 1, UUID.randomUUID(), null));

        when(templateRepository.findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(java.util.Optional.of(template));
        when(templateRepository.save(any(ApprovalTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRuleDto saved = service.saveRule(new ApprovalRuleDto(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Work Order",
                2,
                List.of(
                        new ApprovalRuleDto.Step(1, null, null, "MANAGER", ApprovalRuleDto.ApproverType.ROLE),
                        new ApprovalRuleDto.Step(2, null, null, "SYSTEM_ADMIN", ApprovalRuleDto.ApproverType.ROLE)
                ),
                true
        ));

        assertThat(saved.stepsCount()).isEqualTo(2);
        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::approverRole)
                .containsExactly("MANAGER", "SYSTEM_ADMIN");
        assertThat(template.getApproverId()).isNull();
        assertThat(template.getApproverRole()).isEqualTo("MANAGER");
        assertThat(template.getSteps()).hasSize(2);
        assertThat(template.getSteps()).allSatisfy(step -> assertThat(step.getApproverId()).isNull());
        verify(templateRepository).save(template);
    }

    private ApprovalTemplate template(
            String code,
            String name,
            ApprovalTargetType targetType,
            ApprovalActionType actionType
    ) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setCode(code);
        template.setName(name);
        template.setTargetType(targetType);
        template.setActionType(actionType);
        template.setActive(true);
        return template;
    }

    private ApprovalTemplateStep step(
            ApprovalTemplate template,
            int order,
            UUID approverId,
            String approverRole
    ) {
        ApprovalTemplateStep step = new ApprovalTemplateStep();
        ReflectionTestUtils.setField(step, "id", UUID.randomUUID());
        step.setTemplate(template);
        step.setStepOrder(order);
        step.setApproverId(approverId);
        step.setApproverRole(approverRole);
        return step;
    }
}
