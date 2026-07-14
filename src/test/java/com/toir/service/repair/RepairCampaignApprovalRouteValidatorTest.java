package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignApprovalRouteValidatorTest {

    private final RepairCampaignApprovalRouteValidator validator = new RepairCampaignApprovalRouteValidator();

    @Test
    void classifiesOneStepSystemAdminRouteAsLegacySystemAdminRoute() {
        ApprovalRequest request = new ApprovalRequest();
        request.setStatus(ApprovalStatus.PENDING);
        request.setActionType(ApprovalActionType.APPROVE);
        ApprovalStep step = step(1, "SYSTEM_ADMIN");
        step.setRequest(request);
        request.getSteps().add(step);

        RepairCampaignApprovalRouteValidator.ValidationResult result = validator.validate(request);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(RepairCampaignApprovalRouteValidator.RouteValidationReason.LEGACY_SYSTEM_ADMIN_ROUTE);
        assertThat(result.code()).isEqualTo("NONCANONICAL_REPAIR_CAMPAIGN_ROUTE");
    }

    @Test
    void validatesCanonicalSevenDisciplineRouteInOrder() {
        ApprovalRequest request = new ApprovalRequest();
        request.setStatus(ApprovalStatus.PENDING);
        request.setActionType(ApprovalActionType.APPROVE);
        for (int i = 0; i < RepairCampaignApprovalRouteValidator.REQUIRED_DISCIPLINE_ROLES.size(); i++) {
            ApprovalStep step = step(i + 1, RepairCampaignApprovalRouteValidator.REQUIRED_DISCIPLINE_ROLES.get(i));
            step.setRequest(request);
            request.getSteps().add(step);
        }

        assertThat(validator.validate(request).valid()).isTrue();
    }

    @Test
    void completedRouteRequiresEveryDisciplineApprovedByDistinctNonRequesterActors() {
        UUID requesterId = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest();
        request.setStatus(ApprovalStatus.APPROVED);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setRequesterId(requesterId);
        for (int i = 0; i < RepairCampaignApprovalRouteValidator.REQUIRED_DISCIPLINE_ROLES.size(); i++) {
            ApprovalStep step = step(i + 1, RepairCampaignApprovalRouteValidator.REQUIRED_DISCIPLINE_ROLES.get(i));
            step.setRequest(request);
            step.setDecision(ApprovalDecision.APPROVED);
            step.setDecidedById(i == 0 ? requesterId : UUID.randomUUID());
            request.getSteps().add(step);
        }

        RepairCampaignApprovalRouteValidator.ValidationResult result = validator.validateCompleted(request);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(RepairCampaignApprovalRouteValidator.RouteValidationReason.SEPARATION_OF_DUTY_FAILURE);
    }

    private static ApprovalStep step(int stepNumber, String role) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(stepNumber);
        step.setApproverRole(role);
        step.setDecision(ApprovalDecision.PENDING);
        return step;
    }
}
