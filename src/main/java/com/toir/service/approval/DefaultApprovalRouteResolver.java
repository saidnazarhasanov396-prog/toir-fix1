package com.toir.service.approval;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.security.ApprovalDomainPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultApprovalRouteResolver implements ApprovalRouteResolver {

    private final ApprovalTemplateRepository templateRepository;
    private final LifecycleApprovalRoutePolicy lifecycleRoutePolicy;
    private final ParallelApprovalAssigneeResolver parallelAssigneeResolver;

    @Autowired
    public DefaultApprovalRouteResolver(
            ApprovalTemplateRepository templateRepository,
            LifecycleApprovalRoutePolicy lifecycleRoutePolicy,
            ParallelApprovalAssigneeResolver parallelAssigneeResolver) {
        this.templateRepository = templateRepository;
        this.lifecycleRoutePolicy = lifecycleRoutePolicy;
        this.parallelAssigneeResolver = parallelAssigneeResolver;
    }

    @Override
    public List<CreateApprovalRequest.StepInput> resolveRoute(ApprovalRequest request) {
        return resolveRouteSnapshot(request).steps();
    }

    @Override
    public ApprovalRouteSnapshot resolveRouteSnapshot(ApprovalRequest request) {
        if (request == null || request.getTargetType() == null) {
            return ApprovalRouteSnapshot.sequential(List.of());
        }
        ApprovalActionType actionType = request.getActionType() == null
                ? ApprovalActionType.APPROVE
                : request.getActionType();
        if (lifecycleRoutePolicy.supports(request.getTargetType(), actionType)) {
            LifecycleRouteResolution resolution = resolveLifecycleRoute(request.getTargetType(), actionType);
            return new ApprovalRouteSnapshot(
                    resolution.flowType(),
                    resolution.templateId(),
                    resolution.templateVersion(),
                    resolution.steps());
        }
        if (request.getTargetType() == ApprovalTargetType.PPR_PLAN
                && actionType == ApprovalActionType.APPROVE) {
            ApprovalTemplate template = templateRepository
                    .findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                            ApprovalTargetType.PPR_PLAN,
                            ApprovalActionType.APPROVE)
                    .orElse(null);
            if (template == null || !hasConfiguredApproverStep(template)) {
                return ApprovalRouteSnapshot.sequential(List.of());
            }
            List<CreateApprovalRequest.StepInput> steps = stepsFromTemplate(template);
            if (steps.isEmpty()) {
                return ApprovalRouteSnapshot.sequential(List.of());
            }
            return new ApprovalRouteSnapshot(
                    effectiveFlowType(template),
                    template.getId(),
                    template.getVersion(),
                    steps);
        }
        return templateRepository
                .findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                        request.getTargetType(),
                        actionType
                )
                .or(() -> templateRepository
                        .findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
                                request.getTargetType()
                        ))
                .map(template -> new ApprovalRouteSnapshot(
                        effectiveFlowType(template),
                        template.getId(),
                        template.getVersion(),
                        stepsFromTemplate(template)))
                .filter(snapshot -> !snapshot.steps().isEmpty())
                .orElseGet(() -> ApprovalRouteSnapshot.sequential(permissionFallbackSteps(request)));
    }

    @Override
    public LifecycleRouteResolution resolveLifecycleRoute(
            ApprovalTargetType targetType,
            ApprovalActionType actionType) {
        if (!lifecycleRoutePolicy.supports(targetType, actionType)) {
            return new LifecycleRouteResolution(
                    List.of(),
                    LifecycleApprovalRoutePolicy.Reason.UNSUPPORTED_TARGET_ACTION);
        }

        ApprovalActionType exactAction = actionType == null
                ? ApprovalActionType.APPROVE
                : actionType;
        List<ApprovalTemplate> templates = templateRepository
                .findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                        targetType,
                        exactAction);
        if (templates.isEmpty()) {
            return new LifecycleRouteResolution(
                    List.of(),
                    LifecycleApprovalRoutePolicy.Reason.NO_ACTIVE_TEMPLATE);
        }
        if (templates.size() > 1) {
            return new LifecycleRouteResolution(
                    List.of(),
                    LifecycleApprovalRoutePolicy.Reason.MULTIPLE_ACTIVE_TEMPLATES);
        }

        LifecycleApprovalRoutePolicy.ValidationResult validation =
                lifecycleRoutePolicy.validateTemplate(templates.getFirst());
        if (!validation.valid()) {
            return new LifecycleRouteResolution(List.of(), validation.reason());
        }

        ApprovalTemplate template = templates.getFirst();
        List<CreateApprovalRequest.StepInput> frozenSteps = effectiveFlowType(template) == ApprovalFlowType.PARALLEL_ALL
                ? parallelAssigneeResolver.resolve(template)
                : validation.orderedSteps().stream()
                        .map(step -> new CreateApprovalRequest.StepInput(
                                step.approverId(),
                                step.approverRole()))
                        .toList();
        return new LifecycleRouteResolution(
                frozenSteps,
                validation.reason(),
                effectiveFlowType(template),
                template.getId(),
                template.getVersion());
    }

    private List<CreateApprovalRequest.StepInput> permissionFallbackSteps(ApprovalRequest request) {
        return ApprovalDomainPermissions.approvePermissionFor(request.getTargetType())
                .map(permission -> List.of(new CreateApprovalRequest.StepInput(null, permission)))
                .orElse(List.of());
    }

    private List<CreateApprovalRequest.StepInput> stepsFromTemplate(ApprovalTemplate template) {
        if (effectiveFlowType(template) == ApprovalFlowType.PARALLEL_ALL) {
            return parallelAssigneeResolver.resolve(template);
        }
        return configuredSequentialSteps(template);
    }

    private boolean hasConfiguredApproverStep(ApprovalTemplate template) {
        return template.getSteps().stream()
                .filter(step -> !step.isDeleted())
                .anyMatch(step -> step.getApproverId() != null
                        || (step.getApproverRole() != null
                            && !step.getApproverRole().isBlank()));
    }

    private List<CreateApprovalRequest.StepInput> configuredSequentialSteps(ApprovalTemplate template) {
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

    private ApprovalFlowType effectiveFlowType(ApprovalTemplate template) {
        return template.getFlowType() == null
                ? ApprovalFlowType.SEQUENTIAL
                : template.getFlowType();
    }
}
