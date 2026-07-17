package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalRuleService {

    private final ApprovalTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final LifecycleApprovalRoutePolicy lifecycleRoutePolicy;

    @Transactional(readOnly = true)
    public List<ApprovalRuleDto> listRules() {
        List<ApprovalTemplate> templates = templateRepository.findAllRules();
        Set<UUID> userIds = templates.stream()
                .flatMap(template -> effectiveSteps(template).stream())
                .map(RuleStep::approverId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdInAndIsDeletedFalse(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return templates.stream()
                .sorted(Comparator
                        .comparing((ApprovalTemplate template) -> template.getTargetType().name())
                        .thenComparing(template -> effectiveActionType(template).name())
                        .thenComparing(ApprovalTemplate::getCode))
                .map(template -> toDto(template, usersById))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRuleDto getRule(UUID id) {
        ApprovalTemplate template = templateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval template not found"));
        return toDto(template, loadUsersById(List.of(template)));
    }

    @Transactional
    public ApprovalRuleDto saveRule(ApprovalRuleDto request) {
        validateRule(request);
        ApprovalActionType actionType = effectiveActionType(request.actionType());
        String code = ruleCode(request.targetType(), actionType);
        ApprovalTemplate template;
        if (lifecycleRoutePolicy.supports(request.targetType(), actionType)) {
            template = templateRepository.findByCode(code)
                    .filter(ApprovalTemplate::isDeleted)
                    .orElseGet(ApprovalTemplate::new);
        } else {
            template = templateRepository
                    .findFirstByTargetTypeAndActionTypeAndIsDeletedFalseOrderByCreatedAtDesc(
                            request.targetType(),
                            actionType
                    )
                    .orElseGet(() -> templateRepository.findByCode(code)
                            .map(existing -> {
                                if (!existing.isDeleted()) {
                                    throw RestException.conflict("Approval template code already exists: " + code);
                                }
                                return existing;
                            })
                            .orElseGet(ApprovalTemplate::new));
        }

        return saveRule(template, request);
    }

    @Transactional
    public ApprovalRuleDto updateRule(UUID id, ApprovalRuleDto request) {
        ApprovalTemplate template = templateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval template not found"));
        ApprovalRuleDto effectiveRequest = new ApprovalRuleDto(
                request.targetType() == null ? template.getTargetType() : request.targetType(),
                request.actionType() == null ? effectiveActionType(template) : request.actionType(),
                StringUtils.hasText(request.documentName()) ? request.documentName() : template.getName(),
                request.stepsCount(),
                request.steps(),
                request.active()
        );
        validateRule(effectiveRequest);
        return saveRule(template, effectiveRequest);
    }

    @Transactional
    public void deleteRule(UUID id) {
        ApprovalTemplate template = templateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval template not found"));
        template.setDeleted(true);
        template.setActive(false);
    }

    private ApprovalRuleDto saveRule(ApprovalTemplate template, ApprovalRuleDto request) {
        ApprovalActionType actionType = effectiveActionType(request.actionType());
        String code = ruleCode(request.targetType(), actionType);
        boolean lifecycleRule = lifecycleRoutePolicy.supports(request.targetType(), actionType);
        LifecycleApprovalRoutePolicy.ValidationResult lifecycleValidation = lifecycleRule
                ? validateLifecycleCandidate(request)
                : null;

        if (lifecycleRule && request.active()) {
            rejectOtherActiveLifecycleTemplates(template, request.targetType(), actionType);
        }

        templateRepository.findByCode(code)
                .filter(existing -> template.getId() == null || !existing.getId().equals(template.getId()))
                .ifPresent(existing -> {
                    throw RestException.conflict("Approval template code already exists: " + code);
                });
        template.setCode(code);
        template.setName(StringUtils.hasText(request.documentName())
                ? request.documentName().trim()
                : documentName(request.targetType()));
        template.setTargetType(request.targetType());
        template.setActionType(actionType);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        if (lifecycleRule) {
            LifecycleApprovalRoutePolicy.RouteStep first = lifecycleValidation.orderedSteps().getFirst();
            template.setApproverId(first.approverId());
            template.setApproverRole(first.approverRole());
        } else {
            template.setApproverId(null);
            template.setApproverRole(normalizedRole(request.steps().getFirst().approverRole()));
        }
        template.setActive(request.active());
        template.setDeleted(false);

        if (request.active() && !lifecycleRule) {
            List<ApprovalTemplate> activeTemplates = templateRepository
                    .findAllByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalse(
                            request.targetType(),
                            actionType
                    );
            (activeTemplates == null ? List.<ApprovalTemplate>of() : activeTemplates).stream()
                    .filter(existing -> existing.getId() != null && !existing.getId().equals(template.getId()))
                    .forEach(existing -> existing.setActive(false));
        }

        try {
            template.getSteps().clear();
            if (template.getId() != null) {
                // Flush orphan removals before inserting replacement steps with the same order.
                templateRepository.saveAndFlush(template);
            }

            if (lifecycleRule) {
                lifecycleValidation.orderedSteps().forEach(routeStep -> {
                    ApprovalTemplateStep step = new ApprovalTemplateStep();
                    step.setTemplate(template);
                    step.setStepOrder(routeStep.order());
                    step.setApproverId(routeStep.approverId());
                    step.setApproverRole(routeStep.approverRole());
                    template.getSteps().add(step);
                });
            } else {
                request.steps().stream()
                        .sorted(Comparator.comparingInt(ApprovalRuleDto.Step::order))
                        .forEach(stepRequest -> {
                            ApprovalTemplateStep step = new ApprovalTemplateStep();
                            step.setTemplate(template);
                            step.setStepOrder(stepRequest.order());
                            step.setApproverId(null);
                            step.setApproverRole(normalizedRole(stepRequest.approverRole()));
                            template.getSteps().add(step);
                        });
            }

            ApprovalTemplate saved = templateRepository.saveAndFlush(template);
            return toDto(saved, Map.of());
        } catch (DataIntegrityViolationException ex) {
            if (hasExactConstraint(ex, "uq_active_lifecycle_approval_template")) {
                throw RestException.conflict("MULTIPLE_ACTIVE_TEMPLATES");
            }
            if (isCodeConflict(ex)) {
                throw RestException.conflict("Approval template code already exists: " + code);
            }
            if (isStepOrderConflict(ex)) {
                throw RestException.conflict("Approval template contains duplicate step order");
            }
            throw ex;
        }
    }

    private Map<UUID, User> loadUsersById(List<ApprovalTemplate> templates) {
        Set<UUID> userIds = templates.stream()
                .flatMap(template -> effectiveSteps(template).stream())
                .map(RuleStep::approverId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdInAndIsDeletedFalse(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ApprovalRuleDto toDto(ApprovalTemplate template, Map<UUID, User> usersById) {
        List<ApprovalRuleDto.Step> steps = effectiveSteps(template).stream()
                .sorted(Comparator.comparingInt(RuleStep::order))
                .map(step -> {
                    User user = step.approverId() == null ? null : usersById.get(step.approverId());
                    ApprovalRuleDto.ApproverType type = step.approverId() == null
                            ? ApprovalRuleDto.ApproverType.ROLE
                            : ApprovalRuleDto.ApproverType.USER;
                    return new ApprovalRuleDto.Step(
                            step.order(),
                            step.approverId(),
                            user == null ? null : user.getFullName(),
                            type == ApprovalRuleDto.ApproverType.ROLE ? step.approverRole() : null,
                            type
                    );
                })
                .toList();
        return new ApprovalRuleDto(
                template.getId(),
                template.getTargetType(),
                effectiveActionType(template),
                documentName(template),
                steps.size(),
                steps,
                template.isActive()
        );
    }

    private void validateRule(ApprovalRuleDto request) {
        if (request == null) {
            throw RestException.badRequest("Approval template body is required");
        }
        if (request.targetType() == null) {
            throw RestException.badRequest("targetType is required");
        }
        if (lifecycleRoutePolicy.supports(
                request.targetType(), effectiveActionType(request.actionType()))) {
            validateLifecycleCandidate(request);
            return;
        }
        if (request.steps() == null || request.steps().isEmpty()) {
            throw RestException.badRequest("At least one approval step is required");
        }
        java.util.Set<Integer> orders = new java.util.HashSet<>();
        for (ApprovalRuleDto.Step step : request.steps()) {
            if (step == null) {
                throw RestException.badRequest("Approval step is required");
            }
            if (step.order() < 1) {
                throw RestException.badRequest("step order must be greater than or equal to 1");
            }
            if (!orders.add(step.order())) {
                throw RestException.badRequest("Duplicate approval step order: " + step.order());
            }
            if (!StringUtils.hasText(step.approverRole())) {
                throw RestException.badRequest("approverRole is required for role-based approval templates");
            }
        }
    }

    private LifecycleApprovalRoutePolicy.ValidationResult validateLifecycleCandidate(
            ApprovalRuleDto request
    ) {
        ApprovalTemplate candidate = new ApprovalTemplate();
        candidate.setCode(ruleCode(request.targetType(), effectiveActionType(request.actionType())));
        candidate.setName(StringUtils.hasText(request.documentName())
                ? request.documentName().trim()
                : documentName(request.targetType()));
        candidate.setTargetType(request.targetType());
        candidate.setActionType(effectiveActionType(request.actionType()));
        candidate.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        candidate.setActive(request.active());
        candidate.setDeleted(false);

        List<ApprovalRuleDto.Step> requestSteps = request.steps() == null
                ? List.of()
                : request.steps();
        for (ApprovalRuleDto.Step stepRequest : requestSteps) {
            if (!hasValidLifecycleAssignment(stepRequest)) {
                throw invalidLifecycleSteps();
            }
            ApprovalTemplateStep step = new ApprovalTemplateStep();
            step.setTemplate(candidate);
            step.setStepOrder(stepRequest.order());
            step.setApproverId(stepRequest.approverType() == ApprovalRuleDto.ApproverType.USER
                    ? stepRequest.approverId()
                    : null);
            step.setApproverRole(stepRequest.approverType() == ApprovalRuleDto.ApproverType.ROLE
                    ? normalizedRole(stepRequest.approverRole())
                    : null);
            candidate.getSteps().add(step);
        }

        candidate.getSteps().stream()
                .min(Comparator.comparingInt(ApprovalTemplateStep::getStepOrder))
                .ifPresent(first -> {
                    candidate.setApproverId(first.getApproverId());
                    candidate.setApproverRole(first.getApproverRole());
                });

        LifecycleApprovalRoutePolicy.ValidationResult validation =
                lifecycleRoutePolicy.validateTemplate(candidate);
        if (!validation.valid()) {
            throw invalidLifecycleSteps();
        }
        return validation;
    }

    private boolean hasValidLifecycleAssignment(ApprovalRuleDto.Step step) {
        if (step == null || step.approverType() == null) {
            return false;
        }
        boolean hasApproverId = step.approverId() != null;
        boolean hasApproverRole = StringUtils.hasText(step.approverRole());
        return switch (step.approverType()) {
            case USER -> hasApproverId && !hasApproverRole;
            case ROLE -> !hasApproverId && hasApproverRole;
        };
    }

    private RestException invalidLifecycleSteps() {
        return RestException.badRequest("APPROVAL_TEMPLATE_STEPS_INVALID");
    }

    private void rejectOtherActiveLifecycleTemplates(
            ApprovalTemplate template,
            ApprovalTargetType targetType,
            ApprovalActionType actionType
    ) {
        List<ApprovalTemplate> templates = templateRepository.findAllRules();
        boolean hasOther = (templates == null ? List.<ApprovalTemplate>of() : templates)
                .stream()
                .filter(java.util.Objects::nonNull)
                .filter(ApprovalTemplate::isActive)
                .filter(existing -> !existing.isDeleted())
                .filter(existing -> existing.getTargetType() == targetType)
                .filter(existing -> effectiveActionType(existing) == actionType)
                .anyMatch(existing -> !sameTemplateId(existing, template));
        if (hasOther) {
            throw RestException.conflict("MULTIPLE_ACTIVE_TEMPLATES");
        }
    }

    private boolean sameTemplateId(ApprovalTemplate first, ApprovalTemplate second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private String normalizedRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        return role.trim();
    }

    private List<RuleStep> effectiveSteps(ApprovalTemplate template) {
        List<RuleStep> configured = template.getSteps().stream()
                .filter(step -> !step.isDeleted())
                .map(step -> new RuleStep(step.getStepOrder(), step.getApproverId(), step.getApproverRole()))
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        if (template.getApproverId() != null) {
            return List.of(new RuleStep(1, template.getApproverId(), null));
        }
        String role = template.getApproverRole();
        return StringUtils.hasText(role)
                ? List.of(new RuleStep(1, null, role.trim()))
                : List.of();
    }

    private String documentName(ApprovalTemplate template) {
        if (StringUtils.hasText(template.getName())) {
            return template.getName().trim();
        }
        return documentName(template.getTargetType());
    }

    private String documentName(ApprovalTargetType targetType) {
        String value = targetType.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return java.util.Arrays.stream(value.split(" "))
                .filter(StringUtils::hasText)
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private ApprovalActionType effectiveActionType(ApprovalTemplate template) {
        return effectiveActionType(template.getActionType());
    }

    private ApprovalActionType effectiveActionType(ApprovalActionType actionType) {
        return actionType == null ? ApprovalActionType.APPROVE : actionType;
    }

    private String ruleCode(ApprovalTargetType targetType, ApprovalActionType actionType) {
        return targetType.name() + "_" + effectiveActionType(actionType).name();
    }

    private boolean isCodeConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("approval_templates_code_key")
                || (normalized.contains("approval_templates")
                && normalized.contains("duplicate")
                && normalized.contains("code"));
    }

    private boolean isStepOrderConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains("uq_approval_template_steps_order");
    }

    private boolean hasExactConstraint(Throwable failure, String constraintName) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (containsExactConstraintToken(current.getMessage(), constraintName)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsExactConstraintToken(String message, String constraintName) {
        if (message == null) {
            return false;
        }
        return java.util.Arrays.stream(message.split("[^A-Za-z0-9_]+"))
                .anyMatch(constraintName::equalsIgnoreCase);
    }

    private record RuleStep(int order, UUID approverId, String approverRole) {
    }
}
