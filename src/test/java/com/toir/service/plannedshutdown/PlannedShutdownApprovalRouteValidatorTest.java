package com.toir.service.plannedshutdown;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownApprovalRouteValidatorTest {

    private final PlannedShutdownApprovalRouteValidator validator = new PlannedShutdownApprovalRouteValidator();

    @Test
    void validatesCanonicalProductionThenHseRoute() {
        ApprovalRequest request = new ApprovalRequest();
        request.getSteps().add(step(1, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE));
        request.getSteps().add(step(2, PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE));

        assertThat(validator.validate(request).valid()).isTrue();
    }

    @Test
    void rejectsSystemAdminGenericAndApproverIdOnlyRoutes() {
        assertThat(validator.validateInputs(List.of(new CreateApprovalRequest.StepInput(null, "SYSTEM_ADMIN"))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.LEGACY_SYSTEM_ADMIN_ROUTE);
        assertThat(validator.validateInputs(List.of(new CreateApprovalRequest.StepInput(null, "PLANNED_SHUTDOWN_APPROVE"))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.GENERIC_APPROVE_ROUTE);
        assertThat(validator.validateInputs(List.of(
                new CreateApprovalRequest.StepInput(UUID.randomUUID(), null),
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.APPROVER_ID_SUBSTITUTION);
    }

    @Test
    void rejectsMissingDuplicateUnexpectedAndWrongOrderRoutes() {
        assertThat(validator.validateInputs(List.of(
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.WRONG_STEP_COUNT);
        assertThat(validator.validateInputs(List.of(
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE),
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.DUPLICATE_ROLE);
        assertThat(validator.validateInputs(List.of(
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE),
                new CreateApprovalRequest.StepInput(null, "PLANNED_SHUTDOWN_MANAGER"))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.UNEXPECTED_ROLE);
        assertThat(validator.validateInputs(List.of(
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE),
                new CreateApprovalRequest.StepInput(null, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE))).reason())
                .isEqualTo(PlannedShutdownApprovalRouteValidator.RouteValidationReason.WRONG_ORDER);
    }

    private static ApprovalStep step(int stepNumber, String role) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(stepNumber);
        step.setApproverRole(role);
        step.setDecision(ApprovalDecision.PENDING);
        return step;
    }
}
