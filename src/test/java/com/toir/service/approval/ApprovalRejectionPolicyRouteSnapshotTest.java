package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalRejectionPolicyRouteSnapshotTest {

    @Test
    void snapshotNormalizesMissingPolicyToTerminate() {
        ApprovalRouteSnapshot snapshot = new ApprovalRouteSnapshot(
                null,
                null,
                null,
                null,
                List.of());

        assertThat(snapshot.flowType()).isEqualTo(ApprovalFlowType.SEQUENTIAL);
        assertThat(snapshot.rejectionPolicy()).isEqualTo(ApprovalRejectionPolicy.TERMINATE);
    }

    @Test
    void genericResolverCopiesPolicyFromTemplate() {
        ApprovalTemplateRepository repository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(repository);
        ApprovalTemplate template = template(
                ApprovalTargetType.WORK_ORDER,
                ApprovalRejectionPolicy.RETURN_TO_INITIATOR);
        when(repository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE)).thenReturn(Optional.of(template));

        ApprovalRouteSnapshot snapshot = resolver.resolveRouteSnapshot(request(ApprovalTargetType.WORK_ORDER));

        assertThat(snapshot.rejectionPolicy())
                .isEqualTo(ApprovalRejectionPolicy.RETURN_TO_INITIATOR);
        assertThat(snapshot.templateId()).isEqualTo(template.getId());
        assertThat(snapshot.steps()).containsExactly(new CreateApprovalRequest.StepInput(null, "MANAGER"));
    }

    @Test
    void lifecycleResolverCopiesPolicyFromTemplate() {
        ApprovalTemplateRepository repository = mock(ApprovalTemplateRepository.class);
        DefaultApprovalRouteResolver resolver = resolver(repository);
        ApprovalTemplate template = template(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP);
        when(repository.findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE)).thenReturn(List.of(template));

        LifecycleRouteResolution resolution = resolver.resolveLifecycleRoute(
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE);

        assertThat(resolution.rejectionPolicy())
                .isEqualTo(ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP);
    }

    private DefaultApprovalRouteResolver resolver(ApprovalTemplateRepository repository) {
        return new DefaultApprovalRouteResolver(
                repository,
                new LifecycleApprovalRoutePolicy(),
                new ParallelApprovalAssigneeResolver(
                        mock(UserRepository.class),
                        mock(RoleRepository.class)));
    }

    private ApprovalTemplate template(
            ApprovalTargetType targetType,
            ApprovalRejectionPolicy rejectionPolicy
    ) {
        ApprovalTemplate template = new ApprovalTemplate();
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        template.setVersion(3L);
        template.setCode(targetType.name() + "_APPROVE");
        template.setName("Policy route");
        template.setTargetType(targetType);
        template.setActionType(ApprovalActionType.APPROVE);
        template.setFlowType(ApprovalFlowType.SEQUENTIAL);
        template.setRejectionPolicy(rejectionPolicy);
        template.setActive(true);

        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setTemplate(template);
        step.setStepOrder(1);
        step.setApproverRole("MANAGER");
        template.getSteps().add(step);
        return template;
    }

    private ApprovalRequest request(ApprovalTargetType targetType) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(targetType);
        request.setTargetId(UUID.randomUUID());
        request.setActionType(ApprovalActionType.APPROVE);
        return request;
    }
}
