package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.service.approval.LifecycleApprovalRoutePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepairCampaignApprovalPolicyTest {

    @Test
    void explicitRequestCapturesScopeAndIsTheOnlyOperationEnteringPendingApproval() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = new RepairCampaign();
        campaign.setStatus(RepairCampaignStatus.RESOURCE_CHECK);
        campaign.setScopeVersion(4L);
        when(hasher.hash(campaign)).thenReturn("a".repeat(64));

        policy(hasher).prepareRequest(campaign, 4L);

        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);
        assertThat(campaign.getApprovalScopeVersion()).isEqualTo(4L);
        assertThat(campaign.getApprovalScopeHash()).isEqualTo("a".repeat(64));
    }

    @Test
    void requestApprovalComputesHashBeforeMutatingSnapshotFields() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = new RepairCampaign();
        campaign.setStatus(RepairCampaignStatus.RESOURCE_CHECK);
        campaign.setScopeVersion(4L);
        when(hasher.hash(campaign)).thenAnswer(invocation -> {
            assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.RESOURCE_CHECK);
            assertThat(campaign.getApprovalScopeVersion()).isNull();
            assertThat(campaign.getApprovalScopeHash()).isNull();
            return "c".repeat(64);
        });

        policy(hasher).prepareRequest(campaign, 4L);

        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);
        assertThat(campaign.getApprovalScopeVersion()).isEqualTo(4L);
        assertThat(campaign.getApprovalScopeHash()).isEqualTo("c".repeat(64));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("snapshotMismatches")
    void decisionPreservesExactSnapshotMismatchConflictCodes(String mismatch, String expectedCode) {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        ApprovalRequest request = completedRequest(campaign, "SYSTEM_ADMIN");
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        switch (mismatch) {
            case "scope version" -> campaign.setApprovalScopeVersion(campaign.getScopeVersion() - 1);
            case "scope hash" -> when(hasher.hash(campaign)).thenReturn("0".repeat(64));
            case "campaign version payload" -> request.setPayloadJson(
                    request.getPayloadJson().replace(":9}", ":8}"));
            case "payload" -> request.setPayloadJson(
                    request.getPayloadJson().replace(campaign.getApprovalScopeHash(), "f".repeat(64)));
            default -> throw new IllegalArgumentException("Unknown mismatch: " + mismatch);
        }

        assertThatThrownBy(() -> policy(hasher).validateCompletion(campaign, request))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains(expectedCode);
                });
    }

    static Stream<Arguments> snapshotMismatches() {
        return Stream.of(
                Arguments.of("scope version", "REPAIR_CAMPAIGN_APPROVAL_SCOPE_VERSION_MISMATCH"),
                Arguments.of("scope hash", "REPAIR_CAMPAIGN_APPROVAL_SCOPE_HASH_MISMATCH"),
                Arguments.of("campaign version payload", "REPAIR_CAMPAIGN_APPROVAL_CAMPAIGN_VERSION_MISMATCH"),
                Arguments.of("payload", "REPAIR_CAMPAIGN_APPROVAL_PAYLOAD_MISMATCH")
        );
    }

    @Test
    void decisionAcceptsCompletedOneStepSystemAdminRuntime() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        policy(hasher).validateCompletion(campaign, completedRequest(campaign, "SYSTEM_ADMIN"));
    }

    @Test
    void decisionAcceptsCompletedSevenStepRuntime() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        policy(hasher).validateCompletion(campaign, completedRequest(campaign,
                "REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER",
                "REPAIR_CAMPAIGN_PRODUCTION_APPROVER",
                "REPAIR_CAMPAIGN_WAREHOUSE_APPROVER",
                "REPAIR_CAMPAIGN_PROCUREMENT_APPROVER",
                "REPAIR_CAMPAIGN_FINANCE_APPROVER",
                "REPAIR_CAMPAIGN_HSE_APPROVER",
                "REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER"));
    }

    @Test
    void decisionAcceptsCompletedArbitraryThreeStepRuntime() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        policy(hasher).validateCompletion(campaign,
                completedRequest(campaign, "PLANNER", "FINANCE_REVIEWER", "FINAL_OWNER"));
    }

    @Test
    void malformedPersistedRuntimeMapsToRouteStaleConflict() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        ApprovalRequest request = completedRequest(campaign, "SYSTEM_ADMIN");
        request.getSteps().getFirst().setStepNumber(2);
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        assertThatThrownBy(() -> policy(hasher).validateCompletion(campaign, request))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
                });
    }

    @Test
    void incompletePersistedRuntimeMapsToExistingIncompleteConflict() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        ApprovalRequest request = completedRequest(campaign, "PLANNER", "FINAL_OWNER");
        ApprovalStep last = request.getSteps().getLast();
        last.setDecision(ApprovalDecision.PENDING);
        last.setDecidedById(null);
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        assertThatThrownBy(() -> policy(hasher).validateCompletion(campaign, request))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getMessage())
                            .contains("REPAIR_CAMPAIGN_APPROVAL_INCOMPLETE_DISCIPLINE_ROUTE");
                });
    }

    @Test
    void wrongTargetOrActionMapsToRouteStaleConflict() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        ApprovalRequest request = completedRequest(campaign, "SYSTEM_ADMIN");
        request.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        assertThatThrownBy(() -> policy(hasher).validateCompletion(campaign, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
    }

    @Test
    void legacyDocumentAliasesFinalizeWhenCanonicalTargetFieldsAreNull() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        ApprovalRequest request = completedRequest(campaign, "SYSTEM_ADMIN");
        request.setTargetType(null);
        request.setTargetId(null);
        request.setActionType(null);
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        assertThat(request.getDocumentType()).isEqualTo(ApprovalTargetType.REPAIR_CAMPAIGN.name());
        assertThat(request.getDocumentId()).isEqualTo(campaign.getId());

        policy(hasher).validateCompletion(campaign, request);
    }

    @Test
    void wrongEffectiveActionMapsToRouteStaleConflict() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = readyCampaign();
        ApprovalRequest request = completedRequest(campaign, "SYSTEM_ADMIN");
        request.setActionType(ApprovalActionType.REJECT);
        when(hasher.hash(campaign)).thenReturn(campaign.getApprovalScopeHash());

        assertThatThrownBy(() -> policy(hasher).validateCompletion(campaign, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
    }

    private static RepairCampaignApprovalPolicy policy(RepairCampaignApprovalScopeHasher hasher) {
        return new RepairCampaignApprovalPolicy(hasher, new LifecycleApprovalRoutePolicy());
    }

    private static RepairCampaign readyCampaign() {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
        campaign.setScopeVersion(5L);
        campaign.setApprovalScopeVersion(5L);
        campaign.setApprovalScopeHash("b".repeat(64));
        campaign.setVersion(9L);
        return campaign;
    }

    private static ApprovalRequest completedRequest(RepairCampaign campaign, String... roles) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setTargetId(campaign.getId());
        request.setActionType(ApprovalActionType.APPROVE);
        request.setRequesterId(UUID.randomUUID());
        request.setStatus(ApprovalStatus.APPROVED);
        request.setCurrentStep(roles.length);
        request.setPayloadJson(RepairCampaignApprovalPolicy.payload(campaign));
        List<ApprovalStep> steps = new ArrayList<>();
        for (int index = 0; index < roles.length; index++) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setStepNumber(index + 1);
            step.setApproverRole(roles[index]);
            step.setDecision(ApprovalDecision.APPROVED);
            step.setDecidedById(UUID.randomUUID());
            steps.add(step);
        }
        request.setSteps(steps);
        return request;
    }
}
