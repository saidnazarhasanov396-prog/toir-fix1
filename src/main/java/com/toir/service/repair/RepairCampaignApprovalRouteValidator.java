package com.toir.service.repair;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class RepairCampaignApprovalRouteValidator {

    public static final List<String> REQUIRED_DISCIPLINE_ROLES = List.of(
            "REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER",
            "REPAIR_CAMPAIGN_PRODUCTION_APPROVER",
            "REPAIR_CAMPAIGN_WAREHOUSE_APPROVER",
            "REPAIR_CAMPAIGN_PROCUREMENT_APPROVER",
            "REPAIR_CAMPAIGN_FINANCE_APPROVER",
            "REPAIR_CAMPAIGN_HSE_APPROVER",
            "REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER");

    public enum RouteValidationReason {
        VALID,
        WRONG_STEP_COUNT,
        LEGACY_SYSTEM_ADMIN_ROUTE,
        MISSING_ROLE,
        DUPLICATE_ROLE,
        UNEXPECTED_ROLE,
        WRONG_ORDER,
        INCOMPLETE_DISCIPLINE_ROUTE,
        SEPARATION_OF_DUTY_FAILURE
    }

    public record ValidationResult(
            boolean valid,
            RouteValidationReason reason,
            String code,
            String detail
    ) {
        public static ValidationResult validResult() {
            return new ValidationResult(true, RouteValidationReason.VALID, "VALID", null);
        }

        public static ValidationResult invalid(RouteValidationReason reason, String detail) {
            return new ValidationResult(false, reason, codeFor(reason), detail);
        }

        private static String codeFor(RouteValidationReason reason) {
            return switch (reason) {
                case VALID -> "VALID";
                case INCOMPLETE_DISCIPLINE_ROUTE -> "INCOMPLETE_DISCIPLINE_ROUTE";
                case SEPARATION_OF_DUTY_FAILURE -> "SEPARATION_OF_DUTY_FAILURE";
                default -> "NONCANONICAL_REPAIR_CAMPAIGN_ROUTE";
            };
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

    public ValidationResult validateCompleted(ApprovalRequest request) {
        ValidationResult route = validate(request);
        if (!route.valid()) {
            return route;
        }
        if (request.getStatus() != ApprovalStatus.APPROVED
                || request.getActionType() != ApprovalActionType.APPROVE) {
            return ValidationResult.invalid(RouteValidationReason.INCOMPLETE_DISCIPLINE_ROUTE,
                    "approval request is not completed with APPROVE action");
        }
        List<ApprovalStep> steps = activeSteps(request);
        boolean complete = steps.stream().allMatch(step ->
                step.getDecision() == ApprovalDecision.APPROVED && step.getDecidedById() != null);
        if (!complete) {
            return ValidationResult.invalid(RouteValidationReason.INCOMPLETE_DISCIPLINE_ROUTE,
                    "not every discipline step is approved");
        }
        Set<java.util.UUID> actors = new HashSet<>();
        for (ApprovalStep step : steps) {
            actors.add(step.getDecidedById());
        }
        if (actors.size() != REQUIRED_DISCIPLINE_ROLES.size() || actors.contains(request.getRequesterId())) {
            return ValidationResult.invalid(RouteValidationReason.SEPARATION_OF_DUTY_FAILURE,
                    "discipline approvals require distinct non-requester actors");
        }
        return ValidationResult.validResult();
    }

    public boolean isDisciplineRole(String role) {
        return REQUIRED_DISCIPLINE_ROLES.contains(role);
    }

    private ValidationResult validateSteps(List<ApprovalStep> steps) {
        if (steps.size() == 1 && "SYSTEM_ADMIN".equals(steps.getFirst().getApproverRole())) {
            return ValidationResult.invalid(RouteValidationReason.LEGACY_SYSTEM_ADMIN_ROUTE,
                    "single SYSTEM_ADMIN step is not a repair campaign approval route");
        }
        if (steps.size() != REQUIRED_DISCIPLINE_ROLES.size()) {
            return ValidationResult.invalid(RouteValidationReason.WRONG_STEP_COUNT,
                    "expected " + REQUIRED_DISCIPLINE_ROLES.size() + " steps but found " + steps.size());
        }

        Set<String> seen = new HashSet<>();
        for (ApprovalStep step : steps) {
            String role = step.getApproverRole();
            if (!REQUIRED_DISCIPLINE_ROLES.contains(role)) {
                return ValidationResult.invalid(RouteValidationReason.UNEXPECTED_ROLE,
                        "unexpected role " + role + " at step " + step.getStepNumber());
            }
            if (!seen.add(role)) {
                return ValidationResult.invalid(RouteValidationReason.DUPLICATE_ROLE,
                        "duplicate role " + role);
            }
        }
        for (String requiredRole : REQUIRED_DISCIPLINE_ROLES) {
            if (!seen.contains(requiredRole)) {
                return ValidationResult.invalid(RouteValidationReason.MISSING_ROLE,
                        "missing role " + requiredRole);
            }
        }
        for (int i = 0; i < REQUIRED_DISCIPLINE_ROLES.size(); i++) {
            ApprovalStep step = steps.get(i);
            String expectedRole = REQUIRED_DISCIPLINE_ROLES.get(i);
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
