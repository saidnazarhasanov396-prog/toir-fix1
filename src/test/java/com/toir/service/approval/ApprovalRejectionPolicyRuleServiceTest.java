package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRejectionPolicyRuleServiceTest {

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
    void majorityRequiresAtLeastOneAssignment() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of()),
                "APPROVAL_MAJORITY_REQUIRES_ODD_ASSIGNMENTS");
    }

    @Test
    void majorityRequiresAnOddNumberOfAssignments() {
        assertPolicyFailure(
                policyRule(
                        ApprovalFlowType.PARALLEL_ALL,
                        ApprovalRejectionPolicy.MAJORITY,
                        List.of(roleStep(1, "MANAGER"), roleStep(2, "SYSTEM_ADMIN"))),
                "APPROVAL_MAJORITY_REQUIRES_ODD_ASSIGNMENTS");
    }

    @Test
    void majorityPolicyPersistsForThreeAssignments() {
        stubSave();

        ApprovalRuleDto saved = service.saveRule(policyRule(
                ApprovalFlowType.PARALLEL_ALL,
                ApprovalRejectionPolicy.MAJORITY,
                List.of(
                        roleStep(1, "MANAGER"),
                        roleStep(2, "SYSTEM_ADMIN"),
                        roleStep(3, "DEPARTMENT_HEAD"))));

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
}
