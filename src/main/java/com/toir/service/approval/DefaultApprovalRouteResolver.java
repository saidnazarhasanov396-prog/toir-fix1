package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.enums.ApprovalActionType;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.security.ApprovalDomainPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultApprovalRouteResolver implements ApprovalRouteResolver {

    private final ApprovalTemplateRepository templateRepository;

    @Override
    public List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request) {
        if (request == null || request.getTargetType() == null) {
            return List.of();
        }
        ApprovalActionType actionType = request.getActionType() == null
                ? ApprovalActionType.APPROVE
                : request.getActionType();
        return templateRepository
                .findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                        request.getTargetType(),
                        actionType
                )
                .or(() -> templateRepository
                        .findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                                request.getTargetType()
                        ))
                .map(this::stepsFromTemplate)
                .filter(steps -> !steps.isEmpty())
                .orElseGet(() -> permissionFallbackSteps(request));
    }

    private List<CreateApprovalRequest.StepInput> permissionFallbackSteps(ApprovalRequest request) {
        return ApprovalDomainPermissions.approvePermissionFor(request.getTargetType())
                .map(permission -> List.of(new CreateApprovalRequest.StepInput(null, permission)))
                .orElse(List.of());
    }

    private List<CreateApprovalRequest.StepInput> stepsFromTemplate(ApprovalTemplate template) {
        List<CreateApprovalRequest.StepInput> configuredSteps = template.getSteps().stream()
                .filter(step -> !step.isDeleted())
                .sorted(java.util.Comparator.comparingInt(ApprovalTemplateStep::getStepOrder))
                .map(step -> new CreateApprovalRequest.StepInput(
                        step.getApproverId(),
                        step.getApproverId() == null ? step.getApproverRole() : null
                ))
                .toList();
        if (!configuredSteps.isEmpty()) {
            return configuredSteps;
        }
        if (template.getApproverId() != null) {
            return List.of(new CreateApprovalRequest.StepInput(template.getApproverId(), null));
        }
        if (template.getApproverRole() == null || template.getApproverRole().isBlank()) {
            return List.of();
        }
        return List.of(new CreateApprovalRequest.StepInput(null, template.getApproverRole().trim()));
    }
}
