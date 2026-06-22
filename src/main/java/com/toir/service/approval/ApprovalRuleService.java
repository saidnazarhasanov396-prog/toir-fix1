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
        ApprovalTemplate template = templateRepository
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
        template.setApproverId(null);
        template.setApproverRole(normalizedRole(request.steps().getFirst().approverRole()));
        template.setActive(request.active());
        template.setDeleted(false);

        template.getSteps().clear();
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

        if (request.active()) {
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
            ApprovalTemplate saved = templateRepository.saveAndFlush(template);
            return toDto(saved, Map.of());
        } catch (DataIntegrityViolationException ex) {
            if (isCodeConflict(ex)) {
                throw RestException.conflict("Approval template code already exists: " + code);
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

    private record RuleStep(int order, UUID approverId, String approverRole) {
    }
}
