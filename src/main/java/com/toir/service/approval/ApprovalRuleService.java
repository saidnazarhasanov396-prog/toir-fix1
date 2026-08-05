package com.toir.service.approval;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.ApprovalTieBreakPolicy;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalRuleService {

    private final ApprovalTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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
        List<Assignment> assignments = validateRule(request);
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

        return saveRule(template, request, assignments);
    }

    @Transactional
    public ApprovalRuleDto updateRule(UUID id, ApprovalRuleDto request) {
        ApprovalTemplate template = templateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval template not found"));
        if (request != null
                && request.version() != null
                && !Objects.equals(request.version(), template.getVersion())) {
            throw RestException.conflict("APPROVAL_TEMPLATE_VERSION_CONFLICT");
        }
        ApprovalRuleDto effectiveRequest = new ApprovalRuleDto(
                request.targetType() == null ? template.getTargetType() : request.targetType(),
                request.actionType() == null ? effectiveActionType(template) : request.actionType(),
                StringUtils.hasText(request.documentName()) ? request.documentName() : template.getName(),
                request.stepsCount(),
                request.steps(),
                request.active(),
                request.flowType() == null ? template.getFlowType() : request.flowType(),
                effectiveRejectionPolicy(request.rejectionPolicy()),
                request.tieBreakPolicy(),
                request.version()
        );
        List<Assignment> assignments = validateRule(effectiveRequest);
        return saveRule(template, effectiveRequest, assignments);
    }

    @Transactional
    public void deleteRule(UUID id) {
        ApprovalTemplate template = templateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval template not found"));
        template.setDeleted(true);
        template.setActive(false);
    }

    private ApprovalRuleDto saveRule(
            ApprovalTemplate template, ApprovalRuleDto request, List<Assignment> assignments) {
        ApprovalActionType actionType = effectiveActionType(request.actionType());
        String code = ruleCode(request.targetType(), actionType);
        ApprovalFlowType flowType = effectiveFlowType(request.flowType());
        ApprovalRejectionPolicy rejectionPolicy = effectiveRejectionPolicy(request.rejectionPolicy());
        ApprovalTieBreakPolicy tieBreakPolicy = request.tieBreakPolicy();
        boolean lifecycleTarget = lifecycleRoutePolicy.supports(request.targetType(), actionType);
        boolean lifecycleRule = lifecycleTarget && flowType == ApprovalFlowType.SEQUENTIAL;
        if (lifecycleRule) {
            validateLifecycleCandidate(request, assignments);
        }
        validateActiveUsers(assignments);

        if (lifecycleTarget && request.active()) {
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
        template.setFlowType(flowType);
        template.setRejectionPolicy(rejectionPolicy);
        template.setTieBreakPolicy(tieBreakPolicy);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        Assignment first = assignments.getFirst();
        template.setApproverId(first.approverId());
        template.setApproverRole(first.approverRole());
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

            assignments.forEach(assignment -> {
                ApprovalTemplateStep step = new ApprovalTemplateStep();
                step.setTemplate(template);
                step.setStepOrder(assignment.order());
                step.setApproverId(assignment.approverId());
                step.setApproverRole(assignment.approverRole());
                template.getSteps().add(step);
            });

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
                template.isActive(),
                effectiveFlowType(template.getFlowType()),
                effectiveRejectionPolicy(template.getRejectionPolicy()),
                template.getTieBreakPolicy(),
                template.getVersion()
        );
    }

    private List<Assignment> validateRule(ApprovalRuleDto request) {
        if (request == null) {
            throw RestException.badRequest("Approval template body is required");
        }
        if (request.targetType() == null) {
            throw RestException.badRequest("targetType is required");
        }
        ApprovalFlowType flowType = effectiveFlowType(request.flowType());
        ApprovalRejectionPolicy rejectionPolicy = effectiveRejectionPolicy(request.rejectionPolicy());
        if (flowType == ApprovalFlowType.PARALLEL_ALL
                && rejectionPolicy == ApprovalRejectionPolicy.MAJORITY
                && (request.steps() == null || request.steps().size() < 2)) {
            throw RestException.conflict("APPROVAL_MAJORITY_REQUIRES_AT_LEAST_TWO_ASSIGNMENTS");
        }
        List<Assignment> assignments = validateAssignments(request.steps());
        validateRejectionPolicy(
                flowType,
                rejectionPolicy,
                request.tieBreakPolicy(),
                assignments.size());
        if (rejectionPolicy == ApprovalRejectionPolicy.MAJORITY
                && assignments.stream().anyMatch(
                assignment -> assignment.approverType() != ApprovalRuleDto.ApproverType.USER)) {
            throw RestException.conflict("APPROVAL_MAJORITY_REQUIRES_EXPLICIT_USERS");
        }
        if (flowType == ApprovalFlowType.PARALLEL_ALL) {
            validateParallelAssignments(assignments);
        }
        return assignments;
    }

    private List<Assignment> validateAssignments(List<ApprovalRuleDto.Step> steps) {
        if (steps == null || steps.isEmpty()) {
            throw invalidTemplateSteps();
        }
        List<Assignment> assignments = steps.stream()
                .map(this::validateAssignment)
                .sorted(Comparator.comparingInt(Assignment::order))
                .toList();
        for (int index = 0; index < assignments.size(); index++) {
            if (assignments.get(index).order() != index + 1) {
                throw invalidTemplateSteps();
            }
        }
        return assignments;
    }

    private Assignment validateAssignment(ApprovalRuleDto.Step step) {
        if (step == null || step.approverType() == null || step.order() < 1) {
            throw invalidTemplateSteps();
        }
        String role = normalizedRole(step.approverRole());
        return switch (step.approverType()) {
            case USER -> {
                if (step.approverId() == null || role != null) {
                    throw invalidTemplateSteps();
                }
                yield new Assignment(step.order(), step.approverId(), null, ApprovalRuleDto.ApproverType.USER);
            }
            case ROLE -> {
                if (step.approverId() != null || role == null
                        || !roleRepository.existsByCodeAndIsDeletedFalse(role)) {
                    throw invalidTemplateSteps();
                }
                yield new Assignment(step.order(), null, role, ApprovalRuleDto.ApproverType.ROLE);
            }
        };
    }

    private void validateActiveUsers(List<Assignment> assignments) {
        Set<UUID> userIds = assignments.stream()
                .map(Assignment::approverId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<UUID, User> usersById = userRepository.findAllByIdInAndIsDeletedFalse(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (userIds.stream().map(usersById::get)
                .anyMatch(user -> user == null || user.isDeleted() || user.getStatus() != UserStatus.ACTIVE)) {
            throw RestException.badRequest("PARALLEL_APPROVER_NOT_ACTIVE");
        }
    }

    private void validateParallelAssignments(List<Assignment> assignments) {
        Set<UUID> userIds = new java.util.HashSet<>();
        Set<String> roles = new java.util.HashSet<>();
        for (Assignment assignment : assignments) {
            if (assignment.approverType() == ApprovalRuleDto.ApproverType.USER
                    && !userIds.add(assignment.approverId())) {
                throw invalidTemplateSteps();
            }
            if (assignment.approverType() == ApprovalRuleDto.ApproverType.ROLE
                    && !roles.add(assignment.approverRole())) {
                throw invalidTemplateSteps();
            }
        }
    }

    private LifecycleApprovalRoutePolicy.ValidationResult validateLifecycleCandidate(
            ApprovalRuleDto request, List<Assignment> assignments
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

        assignments.forEach(assignment -> {
            ApprovalTemplateStep step = new ApprovalTemplateStep();
            step.setTemplate(candidate);
            step.setStepOrder(assignment.order());
            step.setApproverId(assignment.approverId());
            step.setApproverRole(assignment.approverRole());
            candidate.getSteps().add(step);
        });

        Assignment first = assignments.getFirst();
        candidate.setApproverId(first.approverId());
        candidate.setApproverRole(first.approverRole());

        LifecycleApprovalRoutePolicy.ValidationResult validation =
                lifecycleRoutePolicy.validateTemplate(candidate);
        if (!validation.valid()) {
            throw invalidTemplateSteps();
        }
        return validation;
    }

    private RestException invalidTemplateSteps() {
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

    private ApprovalRejectionPolicy effectiveRejectionPolicy(ApprovalRejectionPolicy rejectionPolicy) {
        return rejectionPolicy == null ? ApprovalRejectionPolicy.TERMINATE : rejectionPolicy;
    }

    private void validateRejectionPolicy(
            ApprovalFlowType flowType,
            ApprovalRejectionPolicy rejectionPolicy,
            ApprovalTieBreakPolicy tieBreakPolicy,
            int assignmentCount
    ) {
        boolean incompatible = flowType == ApprovalFlowType.SEQUENTIAL
                ? rejectionPolicy == ApprovalRejectionPolicy.MAJORITY
                : rejectionPolicy == ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP;
        if (incompatible) {
            throw RestException.conflict("APPROVAL_REJECTION_POLICY_FLOW_MISMATCH");
        }
        if (rejectionPolicy != ApprovalRejectionPolicy.MAJORITY) {
            if (tieBreakPolicy != null) {
                throw RestException.conflict("APPROVAL_MAJORITY_TIE_BREAK_NOT_APPLICABLE");
            }
            return;
        }
        if (assignmentCount < 2) {
            throw RestException.conflict("APPROVAL_MAJORITY_REQUIRES_AT_LEAST_TWO_ASSIGNMENTS");
        }
        if (assignmentCount % 2 == 0 && tieBreakPolicy == null) {
            throw RestException.conflict("APPROVAL_MAJORITY_TIE_BREAK_REQUIRED");
        }
        if (assignmentCount % 2 != 0 && tieBreakPolicy != null) {
            throw RestException.conflict("APPROVAL_MAJORITY_TIE_BREAK_NOT_APPLICABLE");
        }
    }

    private ApprovalFlowType effectiveFlowType(ApprovalFlowType flowType) {
        return flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
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

    private record Assignment(
            int order,
            UUID approverId,
            String approverRole,
            ApprovalRuleDto.ApproverType approverType) {
    }

    }

