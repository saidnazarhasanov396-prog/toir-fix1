package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
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
    void roleBasedTemplateKeepsRoleOnlyStepUnassigned() {
        ApprovalTemplateRepository templateRepository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = new DefaultApprovalRouteResolver(templateRepository);
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.WORK_ORDER);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole("WORK_ORDER_APPROVER");
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        when(templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalTargetType.WORK_ORDER))
                .thenReturn(Optional.of(template));

        var steps = resolver.resolveRoute(request);

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().approverId()).isNull();
        assertThat(steps.getFirst().approverRole()).isEqualTo("WORK_ORDER_APPROVER");
    }

}
