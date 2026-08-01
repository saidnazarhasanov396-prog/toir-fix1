package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalTieBreakPolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRejectionPolicyRuleServiceTest {

    private static final UUID USER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final ApprovalRuleService service = new ApprovalRuleService(
            templateRepository,
            userRepository,
            roleRepository,
            new LifecycleApprovalRoutePolicy());

    @BeforeEach
    void allowConfiguredRoles() {
        lenient().when(roleRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(true);
    }

    @Test
    void sequentialRejectsMajorityPolicy() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.SEQUENTIAL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of(roleStep(1, "MANAGER"))),
                "APPROVAL_REJECTION_POLICY_FLOW_MISMATCH");
    }

    @Test
    void parallelRejectsReturnToPreviousStepPolicy() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP,
                        List.of(roleStep(1, "MANAGER"))),
                "APPROVAL_REJECTION_POLICY_FLOW_MISMATCH");
    }

    @Test
    void majorityRequiresAtLeastTwoAssignments() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of()),
                "APPROVAL_MAJORITY_REQUIRES_AT_LEAST_TWO_ASSIGNMENTS");
    }

    @Test
    void majorityRequiresTieBreakForEvenAssignments() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of(roleStep(1, "MANAGER"), roleStep(2, "SYSTEM_ADMIN"))),
                "APPROVAL_MAJORITY_TIE_BREAK_REQUIRED");
    }

    @Test
    void majorityRequiresExplicitUsersBecauseRolesExpandAtRuntime() {
        stubSave();

        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of(roleStep(1, "MANAGER"), roleStep(2, "SYSTEM_ADMIN")),
                        ApprovalTieBreakPolicy.REJECT_ON_TIE),
                "APPROVAL_MAJORITY_REQUIRES_EXPLICIT_USERS");
    }

    @Test
    void majorityPersistsExplicitTieBreakForEvenAssignments() {
        stubSave();

        ApprovalRuleDto saved = service.saveRule(policyRule(
                ApprovalFlowType.PARALLEL_ALL,
                ApprovalRejectionPolicy.MAJORITY,
                List.of(userStep(1, USER_1), userStep(2, USER_2)),
                ApprovalTieBreakPolicy.REJECT_ON_TIE));

        assertThat(saved.tieBreakPolicy()).isEqualTo(ApprovalTieBreakPolicy.REJECT_ON_TIE);
        ArgumentCaptor<ApprovalTemplate> captor = ArgumentCaptor.forClass(ApprovalTemplate.class);
        verify(templateRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTieBreakPolicy())
                .isEqualTo(ApprovalTieBreakPolicy.REJECT_ON_TIE);
    }

    @Test
    void majorityRejectsTieBreakForOddAssignments() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of(
                                roleStep(1, "MANAGER"),
                                roleStep(2, "SYSTEM_ADMIN"),
                                roleStep(3, "DEPARTMENT_HEAD")),
                        ApprovalTieBreakPolicy.APPROVE_ON_TIE),
                "APPROVAL_MAJORITY_TIE_BREAK_NOT_APPLICABLE");
    }

    @Test
    void majorityPolicyPersistsForThreeAssignments() {
        stubSave();

        ApprovalRuleDto saved = service.saveRule(policyRule(
                ApprovalFlowType.PARALLEL_ALL,
                ApprovalRejectionPolicy.MAJORITY,
                List.of(
                        userStep(1, USER_1),
                        userStep(2, USER_2),
                        userStep(3, USER_3))));

        assertThat(saved.rejectionPolicy()).isEqualTo(ApprovalRejectionPolicy.MAJORITY);
        ArgumentCaptor<ApprovalTemplate> captor = ArgumentCaptor.forClass(ApprovalTemplate.class);
        verify(templateRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRejectionPolicy()).isEqualTo(ApprovalRejectionPolicy.MAJORITY);
    }

    @Test
    void absentPolicyDefaultsToTerminate() {
        stubSave();
        ApprovalRuleDto legacy = new ApprovalRuleDto(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Legacy",
                1,
                List.of(roleStep(1, "MANAGER")),
                true);

        assertThat(service.saveRule(legacy).rejectionPolicy())
                .isEqualTo(ApprovalRejectionPolicy.TERMINATE);
    }

    private void stubSave() {
        when(templateRepository
                .findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                        ApprovalTargetType.WORK_ORDER,
                        ApprovalActionType.APPROVE))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCode("WORK_ORDER_APPROVE")).thenReturn(Optional.empty());
        when(templateRepository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE)).thenReturn(List.of());
        when(userRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(activeUser(USER_1), activeUser(USER_2), activeUser(USER_3)));
        when(templateRepository.saveAndFlush(any(ApprovalTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertPolicyFailure(ApprovalRuleDto rule, String expectedMessage) {
        assertThatThrownBy(() -> service.saveRule(rule))
                .isInstanceOfSatisfying(RestException.class,
                        error -> assertThat(error.getMessage()).isEqualTo(expectedMessage));
    }

    private ApprovalRuleDto policyRule(
            ApprovalFlowType flowType,
            ApprovalRejectionPolicy rejectionPolicy,
            List<ApprovalRuleDto.Step> steps
    ) {
        return policyRule(flowType, rejectionPolicy, steps, null);
    }

    private ApprovalRuleDto policyRule(
            ApprovalFlowType flowType,
            ApprovalRejectionPolicy rejectionPolicy,
            List<ApprovalRuleDto.Step> steps,
            ApprovalTieBreakPolicy tieBreakPolicy
    ) {
        return new ApprovalRuleDto(
                null,
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Policy",
                steps.size(),
                steps,
                true,
                flowType,
                rejectionPolicy,
                tieBreakPolicy,
                null);
    }

    private ApprovalRuleDto.Step roleStep(int order, String role) {
        return new ApprovalRuleDto.Step(
                order,
                null,
                null,
                role,
                ApprovalRuleDto.ApproverType.ROLE);
    }

    private ApprovalRuleDto.Step userStep(int order, UUID userId) {
        return new ApprovalRuleDto.Step(
                order,
                userId,
                null,
                null,
                ApprovalRuleDto.ApproverType.USER);
    }

    private User activeUser(UUID userId) {
        return User.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build();
    }
}
