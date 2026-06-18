package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalActionType;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
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
                template.getTargetType(),
                effectiveActionType(template),
                documentName(template),
                steps.size(),
                steps,
                template.isActive()
        );
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
        String role = switch (template.getRoutePolicy()) {
            case SYSTEM_ADMIN -> "SYSTEM_ADMIN";
            case DEPARTMENT_HEAD -> "DEPARTMENT_HEAD";
            case ROLE_BASED, USER_BASED -> template.getApproverRole();
        };
        return StringUtils.hasText(role)
                ? List.of(new RuleStep(1, null, role.trim()))
                : List.of();
    }

    private String documentName(ApprovalTemplate template) {
        if (StringUtils.hasText(template.getName())) {
            return template.getName().trim();
        }
        String value = template.getTargetType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return java.util.Arrays.stream(value.split(" "))
                .filter(StringUtils::hasText)
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private ApprovalActionType effectiveActionType(ApprovalTemplate template) {
        return template.getActionType() == null ? ApprovalActionType.APPROVE : template.getActionType();
    }

    private record RuleStep(int order, UUID approverId, String approverRole) {
    }
}
