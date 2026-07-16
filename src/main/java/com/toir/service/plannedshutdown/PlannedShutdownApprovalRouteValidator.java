package com.toir.service.plannedshutdown;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class PlannedShutdownApprovalRouteValidator {

    public static final List<String> REQUIRED_APPROVER_ROLES = List.of(
            PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE,
            PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE);

    public enum RouteValidationReason {
        VALID,
        WRONG_STEP_COUNT,
        LEGACY_SYSTEM_ADMIN_ROUTE,
        GENERIC_APPROVE_ROUTE,
        APPROVER_ID_SUBSTITUTION,
        MISSING_ROLE,
        DUPLICATE_ROLE,
        UNEXPECTED_ROLE,
        WRONG_ORDER
    }

    public record ValidationResult(boolean valid, RouteValidationReason reason, String code, String detail) {
        public static ValidationResult validResult() {
            return new ValidationResult(true, RouteValidationReason.VALID, "VALID", null);
        }

        public static ValidationResult invalid(RouteValidationReason reason, String detail) {
            return new ValidationResult(false, reason, "NONCANONICAL_PLANNED_SHUTDOWN_ROUTE", detail);
        }
    }

    public ValidationResult validate(ApprovalRequest request) {
        if (request == null) {
            return ValidationResult.invalid(RouteValidationReason.WRONG_STEP_COUNT, "approval request is null");
        }
        return validateSteps(activeSteps(request));
    }

    public ValidationResult validateInputs(List<CreateApprovalRequest.StepInput> inputs) {
        if (inputs == null) {
            return validateSteps(List.of());
        }
        List<ApprovalStep> steps = new ArrayList<>();
        int stepNumber = 1;
        for (CreateApprovalRequest.StepInput input : inputs) {
            if (input == null) {
                continue;
            }
            ApprovalStep step = new ApprovalStep();
            step.setStepNumber(stepNumber++);
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            steps.add(step);
        }
        return validateSteps(steps);
    }

    private ValidationResult validateSteps(List<ApprovalStep> steps) {
        if (steps.size() == 1 && "SYSTEM_ADMIN".equals(steps.getFirst().getApproverRole())) {
            return ValidationResult.invalid(RouteValidationReason.LEGACY_SYSTEM_ADMIN_ROUTE,
                    "single SYSTEM_ADMIN step is not a planned shutdown approval route");
        }
        if (steps.size() == 1 && "PLANNED_SHUTDOWN_APPROVE".equals(steps.getFirst().getApproverRole())) {
            return ValidationResult.invalid(RouteValidationReason.GENERIC_APPROVE_ROUTE,
                    "generic PLANNED_SHUTDOWN_APPROVE step is not a planned shutdown approval route");
        }
        if (steps.size() != REQUIRED_APPROVER_ROLES.size()) {
            return ValidationResult.invalid(RouteValidationReason.WRONG_STEP_COUNT,
                    "expected " + REQUIRED_APPROVER_ROLES.size() + " steps but found " + steps.size());
        }

        Set<String> seen = new HashSet<>();
        for (ApprovalStep step : steps) {
            if (step.getApproverId() != null) {
                return ValidationResult.invalid(RouteValidationReason.APPROVER_ID_SUBSTITUTION,
                        "approver-id-only substitution is not allowed at step " + step.getStepNumber());
            }
            String role = step.getApproverRole();
            if (!REQUIRED_APPROVER_ROLES.contains(role)) {
                return ValidationResult.invalid(RouteValidationReason.UNEXPECTED_ROLE,
                        "unexpected role " + role + " at step " + step.getStepNumber());
            }
            if (!seen.add(role)) {
                return ValidationResult.invalid(RouteValidationReason.DUPLICATE_ROLE, "duplicate role " + role);
            }
        }
        for (String requiredRole : REQUIRED_APPROVER_ROLES) {
            if (!seen.contains(requiredRole)) {
                return ValidationResult.invalid(RouteValidationReason.MISSING_ROLE, "missing role " + requiredRole);
            }
        }
        for (int i = 0; i < REQUIRED_APPROVER_ROLES.size(); i++) {
            ApprovalStep step = steps.get(i);
            String expectedRole = REQUIRED_APPROVER_ROLES.get(i);
            int expectedStepNumber = i + 1;
            if (step.getStepNumber() != expectedStepNumber || !Objects.equals(step.getApproverRole(), expectedRole)) {
                return ValidationResult.invalid(RouteValidationReason.WRONG_ORDER,
                        "expected " + expectedRole + " at step " + expectedStepNumber);
            }
        }
        return ValidationResult.validResult();
    }

    private static List<ApprovalStep> activeSteps(ApprovalRequest request) {
        if (request.getSteps() == null) {
            return List.of();
        }
        List<ApprovalStep> steps = new ArrayList<>(request.getSteps().stream()
                .filter(step -> step != null && !step.isDeleted())
                .toList());
        steps.sort(java.util.Comparator.comparingInt(ApprovalStep::getStepNumber));
        return steps;
    }
}
