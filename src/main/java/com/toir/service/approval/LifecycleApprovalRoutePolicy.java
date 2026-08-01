package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public final class LifecycleApprovalRoutePolicy {

    private static final Set<ApprovalTargetType> TARGETS = EnumSet.of(
            ApprovalTargetType.REPAIR_CAMPAIGN,
            ApprovalTargetType.PLANNED_SHUTDOWN);

    public enum Reason {
        VALID,
        UNSUPPORTED_TARGET_ACTION,
        NO_ACTIVE_TEMPLATE,
        MULTIPLE_ACTIVE_TEMPLATES,
        EMPTY_ROUTE,
        NONPOSITIVE_ORDER,
        DUPLICATE_ORDER,
        NONCONTIGUOUS_ORDER,
        INVALID_ASSIGNMENT,
        DUPLICATE_EXPLICIT_APPROVER,
        REQUEST_STATUS_INCONSISTENT,
        CURRENT_STEP_INVALID,
        PRIOR_STEP_INCOMPLETE,
        DECISION_ACTOR_MISSING,
        REQUESTER_DECISION,
        REPEATED_APPROVING_ACTOR,
        ACTOR_INELIGIBLE,
        CURRENT_STEP_ONLY,
        RUNTIME_INCOMPLETE
    }

    public record RouteStep(int order, UUID approverId, String approverRole) {
        public RouteStep {
            approverRole = normalizeRole(approverRole);
        }
    }

    public record ValidationResult(boolean valid, Reason reason, List<RouteStep> orderedSteps) {
        public ValidationResult {
            orderedSteps = orderedSteps == null ? List.of() : List.copyOf(orderedSteps);
        }

        static ValidationResult valid(List<RouteStep> steps) {
            return new ValidationResult(true, Reason.VALID, steps);
        }

        static ValidationResult invalid(Reason reason) {
            return new ValidationResult(false, reason, List.of());
        }
    }

    public boolean supports(ApprovalTargetType target, ApprovalActionType action) {
        return TARGETS.contains(target) && effectiveAction(action) == ApprovalActionType.APPROVE;
    }

    public ValidationResult validateTemplate(ApprovalTemplate template) {
        if (template == null || !supports(template.getTargetType(), template.getActionType())) {
            return ValidationResult.invalid(Reason.UNSUPPORTED_TARGET_ACTION);
        }
        List<ApprovalTemplateStep> steps = template.getSteps() == null
                ? List.of()
                : new ArrayList<>(template.getSteps());
        List<RouteStep> route = steps.stream()
                .filter(step -> step != null && !step.isDeleted())
                .map(step -> new RouteStep(
                        step.getStepOrder(), step.getApproverId(), step.getApproverRole()))
                .toList();
        return validateOrdered(route, true);
    }

    public ValidationResult validateRuntime(ApprovalRequest request) {
        if (request == null || !supports(request.getTargetType(), request.getActionType())) {
            return ValidationResult.invalid(Reason.UNSUPPORTED_TARGET_ACTION);
        }
        List<RouteStep> route = activeRuntimeSteps(request).stream()
                .map(step -> new RouteStep(
                        step.getStepNumber(), step.getApproverId(), step.getApproverRole()))
                .toList();
        ValidationResult structure = validateOrdered(route, false);
        if (!structure.valid()) {
            return structure;
        }
        if (effectiveFlowType(request.getFlowType()) == ApprovalFlowType.PARALLEL_ALL
                && route.stream().anyMatch(step -> step.approverId() == null)) {
            return ValidationResult.invalid(Reason.INVALID_ASSIGNMENT);
        }
        return validateRuntimeState(request);
    }

    public ValidationResult validateDecision(ApprovalRequest request,
                                             ApprovalStep step,
                                             UUID actor,
                                             ApprovalDecision outcome,
                                             boolean assignmentSatisfied) {
        ValidationResult runtime = validateRuntime(request);
        if (!runtime.valid()) {
            return runtime;
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
        }
        if (effectiveFlowType(request.getFlowType()) == ApprovalFlowType.PARALLEL_ALL) {
            ApprovalStep persisted = activeRuntimeSteps(request).stream()
                    .filter(candidate -> representsPersistedStep(step, candidate))
                    .findFirst()
                    .orElse(null);
            if (persisted == null
                    || persisted.getApprovalRound() != request.getApprovalRound()
                    || persisted.getDecision() != ApprovalDecision.PENDING) {
                return ValidationResult.invalid(Reason.CURRENT_STEP_ONLY);
            }
            return validateDecisionActor(request, actor, outcome, assignmentSatisfied, runtime);
        }
        ApprovalStep persistedCurrent = activeRuntimeSteps(request).get(request.getCurrentStep() - 1);
        if (step == null
                || step.isDeleted()
                || step.getStepNumber() != request.getCurrentStep()
                || !representsPersistedStep(step, persistedCurrent)) {
            return ValidationResult.invalid(Reason.CURRENT_STEP_ONLY);
        }
        return validateDecisionActor(request, actor, outcome, assignmentSatisfied, runtime);
    }

    public ValidationResult validateCompletion(ApprovalRequest request) {
        if (request == null || !supports(request.getTargetType(), request.getActionType())) {
            return ValidationResult.invalid(Reason.UNSUPPORTED_TARGET_ACTION);
        }
        List<ApprovalStep> steps = activeRuntimeSteps(request);
        List<RouteStep> route = steps.stream()
                .map(step -> new RouteStep(
                        step.getStepNumber(), step.getApproverId(), step.getApproverRole()))
                .toList();
        ValidationResult structure = validateOrdered(route, false);
        if (!structure.valid()) {
            return structure;
        }
        if (effectiveFlowType(request.getFlowType()) == ApprovalFlowType.PARALLEL_ALL) {
            if (route.stream().anyMatch(step -> step.approverId() == null)) {
                return ValidationResult.invalid(Reason.INVALID_ASSIGNMENT);
            }
            ApprovalRejectionPolicy rejectionPolicy = effectiveRejectionPolicy(request);
            if (rejectionPolicy == ApprovalRejectionPolicy.MAJORITY) {
                boolean allDecided = steps.stream().allMatch(step ->
                        step.getDecision() == ApprovalDecision.APPROVED
                                || step.getDecision() == ApprovalDecision.REJECTED);
                long approved = steps.stream()
                        .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                        .count();
                if (!allDecided || approved <= steps.size() / 2) {
                    return ValidationResult.invalid(Reason.RUNTIME_INCOMPLETE);
                }
            } else if (steps.stream().anyMatch(step -> step.getDecision() != ApprovalDecision.APPROVED)) {
                return ValidationResult.invalid(Reason.RUNTIME_INCOMPLETE);
            }
            ValidationResult evidence = validateApprovedDecisionEvidence(request, steps);
            if (!evidence.valid()) {
                return evidence;
            }
            if (request.getStatus() != ApprovalStatus.PENDING
                    && request.getStatus() != ApprovalStatus.APPROVED) {
                return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            }
            return ValidationResult.valid(structure.orderedSteps());
        }
        if (request.getCurrentStep() != steps.size()) {
            return ValidationResult.invalid(Reason.CURRENT_STEP_INVALID);
        }
        if (steps.stream().anyMatch(step -> step.getDecision() != ApprovalDecision.APPROVED)) {
            return ValidationResult.invalid(Reason.RUNTIME_INCOMPLETE);
        }
        ValidationResult decisionEvidence = validateApprovedDecisionEvidence(request, steps);
        if (!decisionEvidence.valid()) {
            return decisionEvidence;
        }
        if (request.getStatus() != ApprovalStatus.PENDING
                && request.getStatus() != ApprovalStatus.APPROVED) {
            return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
        }
        return completionEvidenceIsValid(request)
                ? ValidationResult.valid(structure.orderedSteps())
                : ValidationResult.invalid(Reason.RUNTIME_INCOMPLETE);
    }

    private ValidationResult validateOrdered(List<RouteStep> route,
                                             boolean rejectDuplicateExplicitIds) {
        if (route == null || route.isEmpty()) {
            return ValidationResult.invalid(Reason.EMPTY_ROUTE);
        }

        List<RouteStep> ordered = new ArrayList<>(route.size());
        for (RouteStep step : route) {
            if (step == null) {
                return ValidationResult.invalid(Reason.INVALID_ASSIGNMENT);
            }
            ordered.add(new RouteStep(step.order(), step.approverId(), step.approverRole()));
        }

        if (ordered.stream().anyMatch(step -> step.order() <= 0)) {
            return ValidationResult.invalid(Reason.NONPOSITIVE_ORDER);
        }

        Set<Integer> orders = new HashSet<>();
        for (RouteStep step : ordered) {
            if (!orders.add(step.order())) {
                return ValidationResult.invalid(Reason.DUPLICATE_ORDER);
            }
        }

        ordered.sort(Comparator.comparingInt(RouteStep::order));
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).order() != index + 1) {
                return ValidationResult.invalid(Reason.NONCONTIGUOUS_ORDER);
            }
        }

        for (RouteStep step : ordered) {
            boolean hasExplicitApprover = step.approverId() != null;
            boolean hasRole = step.approverRole() != null;
            if (hasExplicitApprover == hasRole) {
                return ValidationResult.invalid(Reason.INVALID_ASSIGNMENT);
            }
        }

        if (rejectDuplicateExplicitIds) {
            Set<UUID> explicitApprovers = new HashSet<>();
            for (RouteStep step : ordered) {
                if (step.approverId() != null && !explicitApprovers.add(step.approverId())) {
                    return ValidationResult.invalid(Reason.DUPLICATE_EXPLICIT_APPROVER);
                }
            }
        }

        return ValidationResult.valid(ordered);
    }

    private List<ApprovalStep> activeRuntimeSteps(ApprovalRequest request) {
        if (request.getSteps() == null) {
            return List.of();
        }
        List<ApprovalStep> steps = new ArrayList<>();
        for (ApprovalStep step : request.getSteps()) {
            if (step != null && !step.isDeleted()) {
                steps.add(step);
            }
        }
        steps.sort(Comparator.comparingInt(ApprovalStep::getStepNumber));
        return List.copyOf(steps);
    }

    private ValidationResult validateRuntimeState(ApprovalRequest request) {
        List<ApprovalStep> steps = activeRuntimeSteps(request);
        List<RouteStep> route = steps.stream()
                .map(step -> new RouteStep(
                        step.getStepNumber(), step.getApproverId(), step.getApproverRole()))
                .toList();

        ApprovalStatus status = request.getStatus();
        if (status != ApprovalStatus.PENDING
                && status != ApprovalStatus.APPROVED
                && status != ApprovalStatus.REJECTED
                && status != ApprovalStatus.CANCELLED
                && status != ApprovalStatus.REWORK) {
            return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
        }

        ValidationResult decisionEvidence = validateApprovedDecisionEvidence(request, steps);
        if (!decisionEvidence.valid()) {
            return decisionEvidence;
        }

        if (effectiveFlowType(request.getFlowType()) == ApprovalFlowType.PARALLEL_ALL) {
            return validateParallelRuntimeState(request, steps, route);
        }

        if (status == ApprovalStatus.APPROVED) {
            if (request.getCurrentStep() != steps.size()) {
                return ValidationResult.invalid(Reason.CURRENT_STEP_INVALID);
            }
            if (steps.stream().anyMatch(step -> step.getDecision() != ApprovalDecision.APPROVED)) {
                return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            }
            return ValidationResult.valid(route);
        }

        int currentStep = request.getCurrentStep();
        if (currentStep <= 0 || currentStep > steps.size()) {
            return ValidationResult.invalid(Reason.CURRENT_STEP_INVALID);
        }
        ApprovalStep current = steps.get(currentStep - 1);
        ApprovalDecision expectedCurrentDecision = status == ApprovalStatus.REJECTED || status == ApprovalStatus.REWORK
                ? ApprovalDecision.REJECTED
                : ApprovalDecision.PENDING;
        if (current.getStepNumber() != currentStep
                || current.getDecision() != expectedCurrentDecision) {
            return ValidationResult.invalid(Reason.CURRENT_STEP_INVALID);
        }
        for (int index = 0; index < currentStep - 1; index++) {
            if (steps.get(index).getDecision() != ApprovalDecision.APPROVED) {
                return ValidationResult.invalid(Reason.PRIOR_STEP_INCOMPLETE);
            }
        }
        for (int index = currentStep; index < steps.size(); index++) {
            if (steps.get(index).getDecision() != ApprovalDecision.PENDING) {
                return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            }
        }
        if (status == ApprovalStatus.REJECTED || status == ApprovalStatus.REWORK) {
            UUID actor = current.getDecidedById();
            if (actor == null) {
                return ValidationResult.invalid(Reason.DECISION_ACTOR_MISSING);
            }
            if (Objects.equals(actor, request.getRequesterId())) {
                return ValidationResult.invalid(Reason.REQUESTER_DECISION);
            }
        }
        return ValidationResult.valid(route);
    }

    private ValidationResult validateParallelRuntimeState(ApprovalRequest request,
                                                            List<ApprovalStep> steps,
                                                            List<RouteStep> route) {
        if (request.getCurrentStep() != 0
                || steps.stream().anyMatch(step -> step.getApprovalRound() != request.getApprovalRound())) {
            return ValidationResult.invalid(Reason.CURRENT_STEP_INVALID);
        }
        ApprovalRejectionPolicy rejectionPolicy = effectiveRejectionPolicy(request);
        boolean majority = rejectionPolicy == ApprovalRejectionPolicy.MAJORITY;
        long approved = steps.stream()
                .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                .count();
        boolean onlyVotes = steps.stream().allMatch(step ->
                step.getDecision() == ApprovalDecision.PENDING
                        || step.getDecision() == ApprovalDecision.APPROVED
                        || step.getDecision() == ApprovalDecision.REJECTED);
        boolean allVotesDecided = onlyVotes
                && steps.stream().noneMatch(step -> step.getDecision() == ApprovalDecision.PENDING);
        return switch (request.getStatus()) {
            case PENDING -> steps.stream().anyMatch(step -> step.getDecision() == ApprovalDecision.PENDING)
                    && (majority
                    ? onlyVotes
                    : steps.stream().noneMatch(step -> step.getDecision() == ApprovalDecision.REJECTED
                    || step.getDecision() == ApprovalDecision.CANCELLED))
                    ? ValidationResult.valid(route)
                    : ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            case APPROVED -> (majority
                    ? allVotesDecided && approved > steps.size() / 2
                    : steps.stream().allMatch(step -> step.getDecision() == ApprovalDecision.APPROVED))
                    ? ValidationResult.valid(route)
                    : ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            case REJECTED -> (majority
                    ? allVotesDecided && approved <= steps.size() / 2
                    : steps.stream().anyMatch(step -> step.getDecision() == ApprovalDecision.REJECTED)
                    && steps.stream().noneMatch(step -> step.getDecision() == ApprovalDecision.PENDING)
                    && steps.stream().allMatch(step -> step.getDecision() == ApprovalDecision.APPROVED
                    || step.getDecision() == ApprovalDecision.REJECTED
                    || step.getDecision() == ApprovalDecision.CANCELLED))
                    ? ValidationResult.valid(route)
                    : ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            case REWORK -> rejectionPolicy == ApprovalRejectionPolicy.RETURN_TO_INITIATOR
                    && onlyVotes
                    && steps.stream().anyMatch(step -> step.getDecision() == ApprovalDecision.REJECTED)
                    ? ValidationResult.valid(route)
                    : ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
            case CANCELLED -> ValidationResult.valid(route);
            default -> ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
        };
    }

    private ValidationResult validateDecisionActor(ApprovalRequest request,
                                                     UUID actor,
                                                     ApprovalDecision outcome,
                                                     boolean assignmentSatisfied,
                                                     ValidationResult runtime) {
        if (outcome != ApprovalDecision.APPROVED && outcome != ApprovalDecision.REJECTED) {
            return ValidationResult.invalid(Reason.REQUEST_STATUS_INCONSISTENT);
        }
        if (actor == null) {
            return ValidationResult.invalid(Reason.DECISION_ACTOR_MISSING);
        }
        if (!assignmentSatisfied) {
            return ValidationResult.invalid(Reason.ACTOR_INELIGIBLE);
        }
        if (Objects.equals(actor, request.getRequesterId())) {
            return ValidationResult.invalid(Reason.REQUESTER_DECISION);
        }
        if (outcome == ApprovalDecision.APPROVED && approvedActors(request).contains(actor)) {
            return ValidationResult.invalid(Reason.REPEATED_APPROVING_ACTOR);
        }
        return ValidationResult.valid(runtime.orderedSteps());
    }

    private ValidationResult validateApprovedDecisionEvidence(ApprovalRequest request,
                                                              List<ApprovalStep> steps) {
        Set<UUID> actors = new HashSet<>();
        for (ApprovalStep step : steps) {
            if (step.getDecision() != ApprovalDecision.APPROVED) {
                continue;
            }
            UUID actor = step.getDecidedById();
            if (actor == null) {
                return ValidationResult.invalid(Reason.DECISION_ACTOR_MISSING);
            }
            if (Objects.equals(actor, request.getRequesterId())) {
                return ValidationResult.invalid(Reason.REQUESTER_DECISION);
            }
            if (!actors.add(actor)) {
                return ValidationResult.invalid(Reason.REPEATED_APPROVING_ACTOR);
            }
        }
        return ValidationResult.valid(List.of());
    }

    private Set<UUID> approvedActors(ApprovalRequest request) {
        Set<UUID> actors = new HashSet<>();
        for (ApprovalStep step : activeRuntimeSteps(request)) {
            if (step.getDecision() == ApprovalDecision.APPROVED && step.getDecidedById() != null) {
                actors.add(step.getDecidedById());
            }
        }
        return Set.copyOf(actors);
    }

    private boolean representsPersistedStep(ApprovalStep supplied, ApprovalStep persisted) {
        if (supplied == persisted) {
            return true;
        }
        return supplied.getId() != null
                && persisted.getId() != null
                && supplied.getId().equals(persisted.getId());
    }

    private boolean completionEvidenceIsValid(ApprovalRequest request) {
        if (request.getStatus() != ApprovalStatus.PENDING
                && request.getStatus() != ApprovalStatus.APPROVED) {
            return false;
        }
        List<ApprovalStep> steps = activeRuntimeSteps(request);
        Set<UUID> actors = new HashSet<>();
        for (ApprovalStep step : steps) {
            UUID actor = step.getDecidedById();
            if (step.getDecision() != ApprovalDecision.APPROVED
                    || actor == null
                    || Objects.equals(actor, request.getRequesterId())
                    || !actors.add(actor)) {
                return false;
            }
        }
        return !steps.isEmpty();
    }

    private static ApprovalRejectionPolicy effectiveRejectionPolicy(ApprovalRequest request) {
        return request.getRejectionPolicy() == null
                ? ApprovalRejectionPolicy.TERMINATE
                : request.getRejectionPolicy();
    }

    private static ApprovalActionType effectiveAction(ApprovalActionType action) {
        return action == null ? ApprovalActionType.APPROVE : action;
    }

    private static ApprovalFlowType effectiveFlowType(ApprovalFlowType flowType) {
        return flowType == null ? ApprovalFlowType.SEQUENTIAL : flowType;
    }

    private static String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
