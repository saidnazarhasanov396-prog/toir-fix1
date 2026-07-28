package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest.StepInput;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParallelApprovalAssigneeResolverTest {

    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void resolvesPrimaryAndAdditionalRolesWithExplicitOverlapToUniquePersonalAssignments() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        User alice = user(ALICE_ID, UserStatus.ACTIVE, false, "FINANCE_MANAGER", Set.of());
        User bob = user(BOB_ID, UserStatus.ACTIVE, false, null, Set.of(" FINANCE_MANAGER "));
        User carol = user(CAROL_ID, UserStatus.ACTIVE, false, "AUDITOR", Set.of());
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(carol, bob, alice));

        List<StepInput> result = resolver.resolve(parallelTemplate(
                roleStep(1, " finance_manager "),
                explicitStep(2, BOB_ID),
                roleStep(3, "AUDITOR")));

        assertThat(result).extracting(StepInput::approverId)
                .containsExactly(ALICE_ID, BOB_ID, CAROL_ID);
        assertThat(result).allSatisfy(step -> assertThat(step.approverRole()).isNull());
    }

    @Test
    void sortsMembersOfEachRoleByIdRegardlessOfRepositoryOrder() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(CAROL_ID, UserStatus.ACTIVE, false, "AUDITOR", Set.of()),
                user(ALICE_ID, UserStatus.ACTIVE, false, "AUDITOR", Set.of()),
                user(BOB_ID, UserStatus.ACTIVE, false, "AUDITOR", Set.of())));

        List<StepInput> result = resolver.resolve(parallelTemplate(roleStep(1, "AUDITOR")));

        assertThat(result).extracting(StepInput::approverId)
                .containsExactly(ALICE_ID, BOB_ID, CAROL_ID);
    }

    @Test
    void retainsFirstOccurrenceWhenOneUserMatchesMultipleRoles() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(CAROL_ID, UserStatus.ACTIVE, false, "AUDITOR", Set.of()),
                user(BOB_ID, UserStatus.ACTIVE, false, "FINANCE_MANAGER", Set.of()),
                user(ALICE_ID, UserStatus.ACTIVE, false, "FINANCE_MANAGER", Set.of("AUDITOR"))));

        List<StepInput> result = resolver.resolve(parallelTemplate(
                roleStep(1, "FINANCE_MANAGER"),
                roleStep(2, "AUDITOR")));

        assertThat(result).extracting(StepInput::approverId)
                .containsExactly(ALICE_ID, BOB_ID, CAROL_ID);
    }

    @Test
    void excludesInactiveAndDeletedRoleMembers() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(ALICE_ID, UserStatus.ACTIVE, false, "REVIEWER", Set.of()),
                user(BOB_ID, UserStatus.INACTIVE, false, "REVIEWER", Set.of()),
                user(CAROL_ID, UserStatus.ACTIVE, true, "REVIEWER", Set.of())));

        List<StepInput> result = resolver.resolve(parallelTemplate(roleStep(1, "REVIEWER")));

        assertThat(result).containsExactly(new StepInput(ALICE_ID, null));
    }

    @Test
    void rejectsStaleExplicitUsers() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(
                user(ALICE_ID, UserStatus.ACTIVE, false, "AUDITOR", Set.of())));

        assertThatThrownBy(() -> resolver.resolve(parallelTemplate(explicitStep(1, BOB_ID))))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo("APPROVAL_TEMPLATE_STEPS_INVALID"));
    }

    @Test
    void roleWithoutActiveUsersUsesStableConflictCode() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolve(parallelTemplate(roleStep(1, "EMPTY_ROLE"))))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage())
                                .isEqualTo("PARALLEL_APPROVER_ROLE_HAS_NO_ACTIVE_USERS"));
    }

    @Test
    void rejectsNullAndNonParallelTemplates() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);
        ApprovalTemplate sequential = parallelTemplate(roleStep(1, "AUDITOR"));
        sequential.setFlowType(ApprovalFlowType.SEQUENTIAL);

        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo("APPROVAL_TEMPLATE_STEPS_INVALID"));
        assertThatThrownBy(() -> resolver.resolve(sequential))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo("APPROVAL_TEMPLATE_STEPS_INVALID"));
    }

    @Test
    void rejectsAnEmptyFinalAssignmentSet() {
        UserRepository userRepository = mock(UserRepository.class);
        ParallelApprovalAssigneeResolver resolver = new ParallelApprovalAssigneeResolver(userRepository);

        assertThatThrownBy(() -> resolver.resolve(parallelTemplate()))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo("APPROVAL_TEMPLATE_STEPS_INVALID"));
    }

    private ApprovalTemplate parallelTemplate(ApprovalTemplateStep... steps) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        template.setSteps(new ArrayList<>(List.of(steps)));
        return template;
    }

    private ApprovalTemplateStep roleStep(int order, String roleCode) {
        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setStepOrder(order);
        step.setApproverRole(roleCode);
        return step;
    }

    private ApprovalTemplateStep explicitStep(int order, UUID userId) {
        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setStepOrder(order);
        step.setApproverId(userId);
        return step;
    }

    private User user(UUID id,
                      UserStatus status,
                      boolean deleted,
                      String primaryRole,
                      Set<String> additionalRoles) {
        return User.builder()
                .id(id)
                .status(status)
                .isDeleted(deleted)
                .primaryRole(primaryRole == null ? null : role(primaryRole))
                .roles(additionalRoles.stream().map(this::role).collect(java.util.stream.Collectors.toSet()))
                .build();
    }

    private Role role(String code) {
        return Role.builder().code(code).build();
    }
}
