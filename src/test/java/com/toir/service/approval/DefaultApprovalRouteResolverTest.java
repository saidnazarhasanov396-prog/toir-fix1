package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultApprovalRouteResolverTest {

    @Test
    void roleBasedTemplateResolvesFirstUserWithMatchingRole() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository, userRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("WORK_ORDER_APPROVER");
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        Role role = new Role();
        role.setCode("WORK_ORDER_APPROVER");
        user.setPrimaryRole(role);
        when(templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalTargetType.WORK_ORDER))
                .thenReturn(Optional.of(template));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(user));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().approverId()).isEqualTo(userId);
        assertThat(steps.getFirst().approverRole()).isEqualTo("WORK_ORDER_APPROVER");
    }
}
