package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalRuleServiceTest {

    private final ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApprovalRuleService service = new ApprovalRuleService(
            templateRepository,
            userRepository,
            new LifecycleApprovalRoutePolicy()
    );

    @Test
    void parallelAllPersistsUniqueActiveExplicitUsers() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        User first = User.builder().id(firstId).fullName("First User").status(UserStatus.ACTIVE).build();
        User second = User.builder().id(secondId).fullName("Second User").status(UserStatus.ACTIVE).build();
        when(userRepository.findAllByIdInAndIsDeletedFalse(java.util.Set.of(firstId, secondId)))
                .thenReturn(List.of(first, second));
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE)).thenReturn(Optional.empty());
        when(templateRepository.findByCode("WORK_ORDER_APPROVE")).thenReturn(Optional.empty());
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE)).thenReturn(List.of());
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRuleDto saved = service.saveRule(parallelRule(firstId, secondId));

        assertThat(saved.flowType()).isEqualTo(ApprovalFlowType.PARALLEL_ALL);
        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::approverId)
                .containsExactly(firstId, secondId);
    }

    @Test
    void parallelAllRejectsDuplicateUsers() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.saveRule(parallelRule(userId, userId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("PARALLEL_APPROVERS_MUST_BE_UNIQUE");
    }

    @Test
    void parallelAllRejectsInactiveUser() {
        UUID userId = UUID.randomUUID();
        User inactive = User.builder().id(userId).fullName("Inactive").status(UserStatus.INACTIVE).build();
        when(userRepository.findAllByIdInAndIsDeletedFalse(java.util.Set.of(userId)))
                .thenReturn(List.of(inactive));

        assertThatThrownBy(() -> service.saveRule(parallelRule(userId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("PARALLEL_APPROVER_NOT_ACTIVE");
    }

    @Test
    void parallelAllRejectsRoleAssignment() {
        ApprovalRuleDto invalid = new ApprovalRuleDto(
                null,
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Parallel",
                1,
                List.of(roleStep(1, "MANAGER")),
                true,
                ApprovalFlowType.PARALLEL_ALL,
                null);

        assertThatThrownBy(() -> service.saveRule(invalid))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("PARALLEL_APPROVERS_MUST_BE_USERS");
    }

    @Test
    void updateRejectsStaleTemplateVersion() {
        UUID templateId = UUID.randomUUID();
        ApprovalTemplate template = template(
                "WORK_ORDER_APPROVE",
                "Work Order",
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        );
        ReflectionTestUtils.setField(template, "id", templateId);
        ReflectionTestUtils.setField(template, "version", 7L);
        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));

        ApprovalRuleDto stale = new ApprovalRuleDto(
                templateId,
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Work Order",
                1,
                List.of(roleStep(1, "MANAGER")),
                true,
                ApprovalFlowType.SEQUENTIAL,
                6L
        );

        assertThatThrownBy(() -> service.updateRule(templateId, stale))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("APPROVAL_TEMPLATE_VERSION_CONFLICT");
        verify(templateRepository, never()).saveAndFlush(any(ApprovalTemplate.class));
    }

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
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        template.getSteps().add(step(template, 1, UUID.randomUUID(), null));

        when(templateRepository.findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(java.util.Optional.of(template));
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        verify(templateRepository, times(2)).saveAndFlush(template);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 9})
    void activeLifecycleTemplateAllowsVariableStepCounts(int count) {
        stubActiveLifecycleSave();
        List<ApprovalRuleDto.Step> steps = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(order -> roleStep(order, "APPROVER_" + order))
                .toList();

        ApprovalRuleDto saved = service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN, steps, true));

        ArgumentCaptor<ApprovalTemplate> captor = ArgumentCaptor.forClass(ApprovalTemplate.class);
        verify(templateRepository).saveAndFlush(captor.capture());
        assertThat(saved.stepsCount()).isEqualTo(count);
        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::order)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, count).boxed().toList());
        assertThat(captor.getValue().getSteps()).extracting(ApprovalTemplateStep::getStepOrder)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, count).boxed().toList());
    }

    @Test
    void oneSystemAdminRoleStepSavesForActiveLifecycleTemplate() {
        stubActiveLifecycleSave();

        ApprovalRuleDto saved = service.saveRule(lifecycleRule(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                List.of(roleStep(1, "SYSTEM_ADMIN")),
                true));

        assertThat(saved.steps()).singleElement().satisfies(step -> {
            assertThat(step.approverType()).isEqualTo(ApprovalRuleDto.ApproverType.ROLE);
            assertThat(step.approverId()).isNull();
            assertThat(step.approverRole()).isEqualTo("SYSTEM_ADMIN");
        });
    }

    @Test
    void repeatedRolesSaveForActiveLifecycleTemplate() {
        stubActiveLifecycleSave();

        ApprovalRuleDto saved = service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                List.of(roleStep(1, "MANAGER"), roleStep(2, "MANAGER")),
                true));

        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::approverRole)
                .containsExactly("MANAGER", "MANAGER");
    }

    @Test
    void explicitUserStepRetainsApproverIdAndNullRole() {
        UUID approverId = UUID.randomUUID();
        stubActiveLifecycleSave();

        ApprovalRuleDto saved = service.saveRule(lifecycleRule(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                List.of(userStep(1, approverId)),
                true));

        ArgumentCaptor<ApprovalTemplate> captor = ArgumentCaptor.forClass(ApprovalTemplate.class);
        verify(templateRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSteps()).singleElement().satisfies(step -> {
            assertThat(step.getApproverId()).isEqualTo(approverId);
            assertThat(step.getApproverRole()).isNull();
        });
        assertThat(saved.steps()).singleElement().satisfies(step -> {
            assertThat(step.approverType()).isEqualTo(ApprovalRuleDto.ApproverType.USER);
            assertThat(step.approverId()).isEqualTo(approverId);
            assertThat(step.approverRole()).isNull();
        });
    }

    @Test
    void duplicateExplicitIdsMapToInvalidWithoutTouchingExistingSteps() {
        UUID templateId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalTemplate existing = template(
                "REPAIR_CAMPAIGN_APPROVE",
                "Repair Campaign",
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        );
        ReflectionTestUtils.setField(existing, "id", templateId);
        ApprovalTemplateStep original = step(existing, 1, null, "ORIGINAL_ROLE");
        existing.getSteps().add(original);
        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateRule(templateId, lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                List.of(userStep(1, approverId), userStep(2, approverId)),
                true)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("APPROVAL_TEMPLATE_STEPS_INVALID");
                });

        assertThat(existing.getSteps()).containsExactly(original);
        assertThat(original.getApproverRole()).isEqualTo("ORIGINAL_ROLE");
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignmentXorViolationsMapToInvalid() {
        UUID approverId = UUID.randomUUID();
        assertInvalidLifecycleSteps(List.of(new ApprovalRuleDto.Step(
                1, approverId, null, "MANAGER", ApprovalRuleDto.ApproverType.USER)));
        assertInvalidLifecycleSteps(List.of(new ApprovalRuleDto.Step(
                1, null, null, null, ApprovalRuleDto.ApproverType.ROLE)));
        assertInvalidLifecycleSteps(List.of(new ApprovalRuleDto.Step(
                1, approverId, null, "MANAGER", ApprovalRuleDto.ApproverType.ROLE)));
    }

    @Test
    void emptyActiveLifecycleRouteMapsToInvalid() {
        assertInvalidLifecycleSteps(List.of());
    }

    @Test
    void invalidLifecycleOrdersMapToInvalid() {
        assertInvalidLifecycleSteps(List.of(roleStep(0, "MANAGER")));
        assertInvalidLifecycleSteps(List.of(roleStep(1, "MANAGER"), roleStep(1, "SYSTEM_ADMIN")));
        assertInvalidLifecycleSteps(List.of(roleStep(1, "MANAGER"), roleStep(3, "SYSTEM_ADMIN")));
    }

    @Test
    void secondActiveExactLifecycleTemplateMapsToMultipleActiveTemplates() {
        ApprovalTemplate existing = template(
                "LEGACY_REPAIR_CAMPAIGN_APPROVAL",
                "Existing Repair Campaign",
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE
        );
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(templateRepository.findAllRules()).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                List.of(roleStep(1, "MANAGER")),
                true)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("MULTIPLE_ACTIVE_TEMPLATES");
                });

        assertThat(existing.isActive()).isTrue();
        verify(templateRepository, never()).saveAndFlush(any());
        verify(templateRepository, never())
                .findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                        ApprovalTargetType.REPAIR_CAMPAIGN,
                        ApprovalActionType.APPROVE
                );
    }

    @Test
    void nullActionActiveTemplateConflictsWithDifferentIdApproveRequestBeforeMutation() {
        ApprovalTemplate legacy = template(
                "LEGACY_REPAIR_CAMPAIGN_APPROVAL",
                "Legacy Repair Campaign",
                ApprovalTargetType.REPAIR_CAMPAIGN,
                null
        );
        ReflectionTestUtils.setField(legacy, "id", UUID.randomUUID());
        legacy.getSteps().add(step(legacy, 1, null, "ORIGINAL_ROLE"));
        when(templateRepository.findAllRules()).thenReturn(List.of(legacy));

        assertThatThrownBy(() -> service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                List.of(roleStep(1, "MANAGER")),
                true)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("MULTIPLE_ACTIVE_TEMPLATES");
                });

        assertThat(legacy.isActive()).isTrue();
        assertThat(legacy.getActionType()).isNull();
        assertThat(legacy.getSteps()).extracting(ApprovalTemplateStep::getApproverRole)
                .containsExactly("ORIGINAL_ROLE");
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameIdNullActionLifecycleUpdateIsExcludedFromCardinalityConflict() {
        UUID templateId = UUID.randomUUID();
        ApprovalTemplate existing = template(
                "PLANNED_SHUTDOWN_APPROVE",
                "Planned Shutdown",
                ApprovalTargetType.PLANNED_SHUTDOWN,
                null
        );
        ReflectionTestUtils.setField(existing, "id", templateId);
        existing.getSteps().add(step(existing, 1, null, "OLD_ROLE"));
        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(existing));
        when(templateRepository.findByCode("PLANNED_SHUTDOWN_APPROVE")).thenReturn(Optional.of(existing));
        when(templateRepository.findAllRules()).thenReturn(List.of(existing));
        when(templateRepository.saveAndFlush(existing)).thenReturn(existing);

        ApprovalRuleDto saved = service.updateRule(templateId, lifecycleRule(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                List.of(roleStep(1, "SYSTEM_ADMIN")),
                true));

        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::approverRole)
                .containsExactly("SYSTEM_ADMIN");
        verify(templateRepository, times(2)).saveAndFlush(existing);
    }

    @Test
    void namedLifecycleUniqueIndexViolationMapsToMultipleActiveTemplates() {
        when(templateRepository.findAllRules()).thenReturn(List.of());
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "write failed",
                        new IllegalStateException(
                                "duplicate key violates unique constraint uq_active_lifecycle_approval_template")));

        assertThatThrownBy(() -> service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                List.of(roleStep(1, "MANAGER")),
                true)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("MULTIPLE_ACTIVE_TEMPLATES");
                });
    }

    @Test
    void unrelatedIntegrityViolationIsNotMappedAsLifecycleCardinalityConflict() {
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(
                "duplicate key violates unique constraint some_other_constraint");
        when(templateRepository.findAllRules()).thenReturn(List.of());
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> service.saveRule(lifecycleRule(
                ApprovalTargetType.PLANNED_SHUTDOWN,
                List.of(roleStep(1, "MANAGER")),
                true))).isSameAs(unrelated);
    }

    @Test
    void similarlyNamedLifecycleConstraintSuffixIsNotMapped() {
        DataIntegrityViolationException suffixed = new DataIntegrityViolationException(
                "duplicate key violates unique constraint "
                        + "uq_active_lifecycle_approval_template_archive");
        when(templateRepository.findAllRules()).thenReturn(List.of());
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class))).thenThrow(suffixed);

        assertThatThrownBy(() -> service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                List.of(roleStep(1, "MANAGER")),
                true))).isSameAs(suffixed);
    }

    @Test
    void workOrderKeepsExistingRoleOnlyReplacementAndDeactivationBehavior() {
        ApprovalTemplate edited = template(
                "WORK_ORDER_APPROVE",
                "Work Order",
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        );
        ApprovalTemplate otherActive = template(
                "LEGACY_WORK_ORDER_APPROVE",
                "Legacy Work Order",
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        );
        ReflectionTestUtils.setField(edited, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(otherActive, "id", UUID.randomUUID());
        when(templateRepository.findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(Optional.of(edited));
        when(templateRepository.findByCode("WORK_ORDER_APPROVE")).thenReturn(Optional.of(edited));
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )).thenReturn(List.of(edited, otherActive));
        when(templateRepository.saveAndFlush(edited)).thenReturn(edited);

        ApprovalRuleDto saved = service.saveRule(new ApprovalRuleDto(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Work Order",
                1,
                List.of(roleStep(1, "DEPARTMENT_HEAD")),
                true
        ));

        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::approverRole)
                .containsExactly("DEPARTMENT_HEAD");
        assertThat(otherActive.isActive()).isFalse();
    }

    @Test
    void duplicateGeneratedCodeReturnsConflictBeforeInsert() {
        ApprovalTemplate existing = template(
                "EQUIPMENT_COMMISSIONING_APPROVE",
                "Equipment Commissioning",
                ApprovalTargetType.EQUIPMENT_COMMISSIONING,
                ApprovalActionType.APPROVE
        );
        when(templateRepository.findByCode("EQUIPMENT_COMMISSIONING_APPROVE"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveRule(rule(
                ApprovalTargetType.EQUIPMENT_COMMISSIONING,
                ApprovalActionType.APPROVE
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(
                            "Approval template code already exists: EQUIPMENT_COMMISSIONING_APPROVE");
                });
    }

    @Test
    void recreatingRuleRestoresSoftDeletedTemplateWithTheSameCode() {
        ApprovalTemplate deleted = template(
                "EQUIPMENT_COMMISSIONING_APPROVE",
                "Old Equipment Commissioning",
                ApprovalTargetType.EQUIPMENT_COMMISSIONING,
                ApprovalActionType.APPROVE
        );
        ReflectionTestUtils.setField(deleted, "id", UUID.randomUUID());
        deleted.setDeleted(true);
        deleted.setActive(false);
        deleted.getSteps().add(step(deleted, 1, null, "OLD_ROLE"));

        when(templateRepository.findByCode("EQUIPMENT_COMMISSIONING_APPROVE"))
                .thenReturn(Optional.of(deleted));
        when(templateRepository.saveAndFlush(deleted)).thenReturn(deleted);

        ApprovalRuleDto saved = service.saveRule(rule(
                ApprovalTargetType.EQUIPMENT_COMMISSIONING,
                ApprovalActionType.APPROVE
        ));

        assertThat(deleted.isDeleted()).isFalse();
        assertThat(deleted.isActive()).isTrue();
        assertThat(saved.steps()).extracting(ApprovalRuleDto.Step::approverRole)
                .containsExactly("DEPARTMENT_HEAD");
        verify(templateRepository, times(2)).saveAndFlush(deleted);
    }

    @Test
    void concurrentDuplicateCodeViolationReturnsConflict() {
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates unique constraint approval_templates_code_key"));

        assertThatThrownBy(() -> service.saveRule(rule(
                ApprovalTargetType.EQUIPMENT_COMMISSIONING,
                ApprovalActionType.APPROVE
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("EQUIPMENT_COMMISSIONING_APPROVE");
                });
    }

    @Test
    void duplicateStepOrderViolationReturnsConflict() {
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates unique constraint uq_approval_template_steps_order"));

        assertThatThrownBy(() -> service.saveRule(rule(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(
                            "Approval template contains duplicate step order");
                });
    }

    private void assertInvalidLifecycleSteps(List<ApprovalRuleDto.Step> steps) {
        assertThatThrownBy(() -> service.saveRule(lifecycleRule(
                ApprovalTargetType.REPAIR_CAMPAIGN, steps, true)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("APPROVAL_TEMPLATE_STEPS_INVALID");
                });
        verify(templateRepository, never()).saveAndFlush(any());
    }

    private void stubActiveLifecycleSave() {
        when(templateRepository.findAllRules()).thenReturn(List.of());
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ApprovalRuleDto lifecycleRule(
            ApprovalTargetType targetType,
            List<ApprovalRuleDto.Step> steps,
            boolean active
    ) {
        return new ApprovalRuleDto(
                targetType,
                ApprovalActionType.APPROVE,
                "Lifecycle Approval",
                steps.size(),
                steps,
                active
        );
    }

    private ApprovalRuleDto parallelRule(UUID... userIds) {
        List<ApprovalRuleDto.Step> steps = java.util.stream.IntStream.range(0, userIds.length)
                .mapToObj(index -> userStep(index + 1, userIds[index]))
                .toList();
        return new ApprovalRuleDto(
                null,
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Parallel",
                steps.size(),
                steps,
                true,
                ApprovalFlowType.PARALLEL_ALL,
                null);
    }

    private ApprovalRuleDto.Step roleStep(int order, String role) {
        return new ApprovalRuleDto.Step(
                order, null, null, role, ApprovalRuleDto.ApproverType.ROLE);
    }

    private ApprovalRuleDto.Step userStep(int order, UUID approverId) {
        return new ApprovalRuleDto.Step(
                order, approverId, null, null, ApprovalRuleDto.ApproverType.USER);
    }

    private ApprovalRuleDto rule(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return new ApprovalRuleDto(
                targetType,
                actionType,
                "Test",
                1,
                List.of(new ApprovalRuleDto.Step(
                        1, null, null, "DEPARTMENT_HEAD", ApprovalRuleDto.ApproverType.ROLE)),
                true
        );
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
