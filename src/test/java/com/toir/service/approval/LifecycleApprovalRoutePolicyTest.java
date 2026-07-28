package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason;
import com.toir.service.approval.LifecycleApprovalRoutePolicy.RouteStep;
import com.toir.service.approval.LifecycleApprovalRoutePolicy.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.ACTOR_INELIGIBLE;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.CURRENT_STEP_INVALID;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.CURRENT_STEP_ONLY;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.DECISION_ACTOR_MISSING;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.DUPLICATE_EXPLICIT_APPROVER;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.DUPLICATE_ORDER;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.EMPTY_ROUTE;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.INVALID_ASSIGNMENT;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.NONCONTIGUOUS_ORDER;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.NONPOSITIVE_ORDER;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.PRIOR_STEP_INCOMPLETE;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.REPEATED_APPROVING_ACTOR;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.REQUESTER_DECISION;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.REQUEST_STATUS_INCONSISTENT;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.RUNTIME_INCOMPLETE;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.UNSUPPORTED_TARGET_ACTION;
import static com.toir.service.approval.LifecycleApprovalRoutePolicy.Reason.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleApprovalRoutePolicyTest {

    private static final UUID EXPLICIT_APPROVER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final LifecycleApprovalRoutePolicy policy = new LifecycleApprovalRoutePolicy();

    @Test
    void declaresTheExactNeutralReasonContract() {
        assertThat(Reason.values()).containsExactly(
                VALID, UNSUPPORTED_TARGET_ACTION, Reason.NO_ACTIVE_TEMPLATE,
                Reason.MULTIPLE_ACTIVE_TEMPLATES, EMPTY_ROUTE, NONPOSITIVE_ORDER,
                DUPLICATE_ORDER, NONCONTIGUOUS_ORDER, INVALID_ASSIGNMENT,
                DUPLICATE_EXPLICIT_APPROVER, REQUEST_STATUS_INCONSISTENT,
                CURRENT_STEP_INVALID, PRIOR_STEP_INCOMPLETE, DECISION_ACTOR_MISSING,
                REQUESTER_DECISION, REPEATED_APPROVING_ACTOR, ACTOR_INELIGIBLE,
                CURRENT_STEP_ONLY, RUNTIME_INCOMPLETE);
    }

    @ParameterizedTest
    @MethodSource("supportedPairs")
    void supportsOnlyLifecycleApprovePairs(ApprovalTargetType target, ApprovalActionType action) {
        assertThat(policy.supports(target, action)).isTrue();
    }

    private static Stream<Arguments> supportedPairs() {
        return Stream.of(
                Arguments.of(ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE),
                Arguments.of(ApprovalTargetType.PLANNED_SHUTDOWN, ApprovalActionType.APPROVE),
                Arguments.of(ApprovalTargetType.REPAIR_CAMPAIGN, null),
                Arguments.of(ApprovalTargetType.PLANNED_SHUTDOWN, null));
    }

    @ParameterizedTest
    @MethodSource("unsupportedPairs")
    void rejectsOtherTargetActionPairs(ApprovalTargetType target, ApprovalActionType action) {
        assertThat(policy.supports(target, action)).isFalse();
    }

    private static Stream<Arguments> unsupportedPairs() {
        return Stream.of(
                Arguments.of(ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE),
                Arguments.of(ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.REJECT),
                Arguments.of(ApprovalTargetType.PLANNED_SHUTDOWN, ApprovalActionType.CANCEL),
                Arguments.of(null, ApprovalActionType.APPROVE));
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalTargetType.class, names = {"REPAIR_CAMPAIGN", "PLANNED_SHUTDOWN"})
    void validatesBothLifecycleTargetsWithLegacyNullApproveAction(ApprovalTargetType target) {
        ApprovalTemplate template = template(roleRoute(1, "APPROVER"));
        template.setTargetType(target);
        template.setActionType(null);
        ApprovalRequest request = pendingRequest(roleRoute(1, "APPROVER"));
        request.setTargetType(target);
        request.setActionType(null);

        assertThat(policy.validateTemplate(template).reason()).isEqualTo(VALID);
        assertThat(policy.validateRuntime(request).reason()).isEqualTo(VALID);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 11})
    void acceptsAnyContiguousTemplateLength(int count) {
        ApprovalTemplate template = roleTemplate(count, "APPROVER");
        ValidationResult result = policy.validateTemplate(template);
        assertThat(result.valid()).isTrue();
        assertThat(result.orderedSteps()).extracting(RouteStep::order)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, count).boxed().toList());
    }

    @Test
    void sortsACopiedTemplateRouteAndReturnsAnImmutableNormalizedList() {
        ApprovalTemplate template = template(
                roleRoute(3, "  THIRD  "),
                roleRoute(1, "FIRST"),
                roleRoute(2, "SECOND"));

        ValidationResult result = policy.validateTemplate(template);

        assertThat(result.orderedSteps()).containsExactly(
                roleRoute(1, "FIRST"),
                roleRoute(2, "SECOND"),
                roleRoute(3, "THIRD"));
        assertThat(template.getSteps()).extracting(ApprovalTemplateStep::getStepOrder)
                .containsExactly(3, 1, 2);
        assertThatThrownBy(() -> result.orderedSteps().add(roleRoute(4, "FOURTH")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsRepeatedRolesAndMixedAssignmentKinds() {
        ValidationResult result = policy.validateTemplate(template(
                roleRoute(1, "APPROVER"),
                roleRoute(2, "APPROVER"),
                explicitRoute(3, EXPLICIT_APPROVER)));

        assertThat(result.reason()).isEqualTo(VALID);
        assertThat(result.orderedSteps()).containsExactly(
                roleRoute(1, "APPROVER"),
                roleRoute(2, "APPROVER"),
                explicitRoute(3, EXPLICIT_APPROVER));
    }

    @Test
    void allowsRoleConfiguredParallelTemplateBeforePersonalExpansion() {
        ApprovalTemplate template = template(roleRoute(1, "CAMPAIGN_APPROVER"));
        template.setFlowType(ApprovalFlowType.PARALLEL_ALL);

        assertThat(policy.validateTemplate(template).reason()).isEqualTo(VALID);
    }

    @ParameterizedTest(name = "template structure: {0}")
    @MethodSource("invalidTemplateRoutes")
    void rejectsInvalidTemplateStructure(String description, RouteStep[] route, Reason reason) {
        assertThat(policy.validateTemplate(template(route)).reason()).isEqualTo(reason);
    }

    private static Stream<Arguments> invalidTemplateRoutes() {
        UUID duplicate = UUID.fromString("20000000-0000-0000-0000-000000000002");
        return Stream.of(
                Arguments.of("empty", new RouteStep[0], EMPTY_ROUTE),
                Arguments.of("zero order", routes(roleRoute(0, "A")), NONPOSITIVE_ORDER),
                Arguments.of("negative order", routes(roleRoute(-1, "A")), NONPOSITIVE_ORDER),
                Arguments.of("duplicate order", routes(roleRoute(1, "A"), roleRoute(1, "B")), DUPLICATE_ORDER),
                Arguments.of("gap", routes(roleRoute(1, "A"), roleRoute(3, "B")), NONCONTIGUOUS_ORDER),
                Arguments.of("missing assignment", routes(new RouteStep(1, null, "  ")), INVALID_ASSIGNMENT),
                Arguments.of("both assignments", routes(new RouteStep(1, duplicate, "A")), INVALID_ASSIGNMENT),
                Arguments.of("duplicate explicit approver",
                        routes(explicitRoute(1, duplicate), explicitRoute(2, duplicate)),
                        DUPLICATE_EXPLICIT_APPROVER));
    }

    @Test
    void ignoresDeletedAndNullTemplateSteps() {
        ApprovalTemplate template = template(roleRoute(1, "ACTIVE"));
        ApprovalTemplateStep deleted = templateStep(roleRoute(9, "DELETED"));
        deleted.setDeleted(true);
        template.getSteps().add(null);
        template.getSteps().add(deleted);

        assertThat(policy.validateTemplate(template).orderedSteps())
                .containsExactly(roleRoute(1, "ACTIVE"));
    }

    @Test
    void rejectsNullOrUnsupportedTemplates() {
        ApprovalTemplate wrongTarget = template(roleRoute(1, "A"));
        wrongTarget.setTargetType(ApprovalTargetType.WORK_ORDER);
        ApprovalTemplate wrongAction = template(roleRoute(1, "A"));
        wrongAction.setActionType(ApprovalActionType.REJECT);

        assertThat(policy.validateTemplate(null).reason()).isEqualTo(UNSUPPORTED_TARGET_ACTION);
        assertThat(policy.validateTemplate(wrongTarget).reason()).isEqualTo(UNSUPPORTED_TARGET_ACTION);
        assertThat(policy.validateTemplate(wrongAction).reason()).isEqualTo(UNSUPPORTED_TARGET_ACTION);
    }

    @ParameterizedTest(name = "runtime structure: {0}")
    @MethodSource("invalidRuntimeRoutes")
    void rejectsInvalidPersistedRuntimeStructure(String description, RouteStep[] route, Reason reason) {
        assertThat(policy.validateRuntime(pendingRequest(route)).reason()).isEqualTo(reason);
    }

    private static Stream<Arguments> invalidRuntimeRoutes() {
        return Stream.of(
                Arguments.of("empty", new RouteStep[0], EMPTY_ROUTE),
                Arguments.of("zero order", routes(roleRoute(0, "A")), NONPOSITIVE_ORDER),
                Arguments.of("duplicate order", routes(roleRoute(1, "A"), roleRoute(1, "B")), DUPLICATE_ORDER),
                Arguments.of("gap", routes(roleRoute(1, "A"), roleRoute(3, "B")), NONCONTIGUOUS_ORDER),
                Arguments.of("missing assignment", routes(new RouteStep(1, null, null)), INVALID_ASSIGNMENT),
                Arguments.of("both assignments",
                        routes(new RouteStep(1, EXPLICIT_APPROVER, "A")), INVALID_ASSIGNMENT));
    }

    @Test
    void runtimeUsesADeletedStepFilteredSortedCopyAndAllowsSnapshotDuplicateExplicitAssignments() {
        ApprovalRequest request = pendingRequest(
                explicitRoute(2, EXPLICIT_APPROVER),
                explicitRoute(1, EXPLICIT_APPROVER));
        request.setCurrentStep(1);
        ApprovalStep deleted = runtimeStep(roleRoute(7, "DELETED"));
        deleted.setDeleted(true);
        request.getSteps().add(null);
        request.getSteps().add(deleted);

        ValidationResult result = policy.validateRuntime(request);

        assertThat(result.reason()).isEqualTo(VALID);
        assertThat(result.orderedSteps()).containsExactly(
                explicitRoute(1, EXPLICIT_APPROVER), explicitRoute(2, EXPLICIT_APPROVER));
        assertThat(request.getSteps()).extracting(step -> step == null ? null : step.getStepNumber())
                .containsExactly(2, 1, null, 7);
        assertThatThrownBy(() -> result.orderedSteps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsRoleBasedParallelRuntimeStepsBeforeTheyCanBeActionable() {
        ApprovalRequest request = pendingRequest(roleRoute(1, "CAMPAIGN_APPROVER"));
        request.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        request.setCurrentStep(0);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(INVALID_ASSIGNMENT);
    }

    @Test
    void rejectsDecisionsOnRoleBasedParallelRuntimeSteps() {
        ApprovalRequest request = pendingRequest(roleRoute(1, "CAMPAIGN_APPROVER"));
        request.setFlowType(ApprovalFlowType.PARALLEL_ALL);
        request.setCurrentStep(0);

        assertThat(policy.validateDecision(
                request,
                request.getSteps().getFirst(),
                UUID.randomUUID(),
                ApprovalDecision.APPROVED,
                true).reason()).isEqualTo(INVALID_ASSIGNMENT);
    }

    @Test
    void acceptsPendingRuntimeWithApprovedPriorsAndPendingSuccessors() {
        UUID requester = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(requester,
                approvedStep(1, UUID.randomUUID()),
                pendingRoleStep(2, "SECOND"),
                pendingRoleStep(3, "THIRD"));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(VALID);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, -1})
    void rejectsOutOfRangePendingCurrentStep(int currentStep) {
        ApprovalRequest request = pendingRequest(roleRoute(1, "A"), roleRoute(2, "B"));
        request.setCurrentStep(currentStep);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(CURRENT_STEP_INVALID);
    }

    @Test
    void requiresThePendingCurrentStepToBePending() {
        ApprovalRequest request = pendingRequest(UUID.randomUUID(), approvedStep(1, UUID.randomUUID()));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(CURRENT_STEP_INVALID);
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalDecision.class, names = {"PENDING", "REJECTED", "RETURNED"})
    void requiresEveryPriorStepToBeApproved(ApprovalDecision priorDecision) {
        ApprovalStep prior = pendingRoleStep(1, "FIRST");
        prior.setDecision(priorDecision);
        ApprovalRequest request = pendingRequest(UUID.randomUUID(), prior, pendingRoleStep(2, "SECOND"));
        request.setCurrentStep(2);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(PRIOR_STEP_INCOMPLETE);
    }

    @Test
    void rejectsACompletedSuccessorOnAPendingRequest() {
        ApprovalRequest request = pendingRequest(UUID.randomUUID(),
                pendingRoleStep(1, "FIRST"), approvedStep(2, UUID.randomUUID()));
        request.setCurrentStep(1);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
    }

    @Test
    void rejectsAnApprovedStepWithoutADecisionActor() {
        ApprovalStep prior = pendingRoleStep(1, "FIRST");
        prior.setDecision(ApprovalDecision.APPROVED);
        ApprovalRequest request = pendingRequest(UUID.randomUUID(), prior, pendingRoleStep(2, "SECOND"));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(DECISION_ACTOR_MISSING);
    }

    @Test
    void rejectsPersistedApprovalByTheRequester() {
        UUID requester = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(requester,
                approvedStep(1, requester), pendingRoleStep(2, "SECOND"));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(REQUESTER_DECISION);
    }

    @Test
    void rejectsRepeatedPersistedApprovingActors() {
        UUID actor = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(UUID.randomUUID(),
                approvedStep(1, actor), approvedStep(2, actor), pendingRoleStep(3, "THIRD"));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(REPEATED_APPROVING_ACTOR);
    }

    @Test
    void acceptsApprovedRuntimeOnlyWithFinalCurrentStepAndCompleteEvidence() {
        ApprovalRequest request = approvedRequest(
                approvedStep(1, UUID.randomUUID()),
                approvedStep(2, UUID.randomUUID()),
                approvedStep(3, UUID.randomUUID()));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(VALID);

        request.setCurrentStep(2);
        assertThat(policy.validateRuntime(request).reason()).isEqualTo(CURRENT_STEP_INVALID);
    }

    @Test
    void rejectsApprovedRequestWithAnIncompleteStep() {
        ApprovalRequest request = approvedRequest(
                approvedStep(1, UUID.randomUUID()), pendingRoleStep(2, "SECOND"));

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {"DRAFT", "EXPIRED", "FAILED"})
    void administrativeRequestStatusesAreInconsistent(ApprovalStatus status) {
        ApprovalRequest request = pendingRequest(roleRoute(1, "A"));
        request.setStatus(status);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
    }

    @Test
    void rejectedAndCancelledRoutesRemainReadableButNonActionable() {
        UUID requester = UUID.randomUUID();
        ApprovalStep approved = approvedStep(1, UUID.randomUUID());
        ApprovalStep rejected = pendingRoleStep(2, "SECOND");
        rejected.setDecision(ApprovalDecision.REJECTED);
        rejected.setDecidedById(UUID.randomUUID());
        ApprovalRequest rejectedRequest = request(
                ApprovalStatus.REJECTED, requester, approved, rejected, pendingRoleStep(3, "THIRD"));
        rejectedRequest.setCurrentStep(2);
        ApprovalRequest cancelledRequest = pendingRequest(
                requester, approvedStep(1, UUID.randomUUID()), pendingRoleStep(2, "SECOND"));
        cancelledRequest.setStatus(ApprovalStatus.CANCELLED);

        assertThat(policy.validateRuntime(rejectedRequest).reason()).isEqualTo(VALID);
        assertThat(policy.validateRuntime(cancelledRequest).reason()).isEqualTo(VALID);
        assertThat(policy.validateDecision(rejectedRequest, rejected, UUID.randomUUID(),
                ApprovalDecision.REJECTED, true).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
        assertThat(policy.validateDecision(cancelledRequest, cancelledRequest.getSteps().get(1), UUID.randomUUID(),
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
        assertThat(policy.validateCompletion(rejectedRequest).reason()).isEqualTo(CURRENT_STEP_INVALID);
        assertThat(policy.validateCompletion(cancelledRequest).reason()).isEqualTo(RUNTIME_INCOMPLETE);
    }

    @Test
    void persistedRejectedDecisionRequiresANonRequesterActor() {
        UUID requester = UUID.randomUUID();
        ApprovalStep rejected = pendingRoleStep(1, "APPROVER");
        rejected.setDecision(ApprovalDecision.REJECTED);
        ApprovalRequest request = request(ApprovalStatus.REJECTED, requester, rejected);
        request.setCurrentStep(1);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(DECISION_ACTOR_MISSING);

        rejected.setDecidedById(requester);
        assertThat(policy.validateRuntime(request).reason()).isEqualTo(REQUESTER_DECISION);
    }

    @Test
    void rejectsNullOrUnsupportedRuntimeRequests() {
        ApprovalRequest wrongTarget = pendingRequest(roleRoute(1, "A"));
        wrongTarget.setTargetType(ApprovalTargetType.WORK_ORDER);
        ApprovalRequest wrongAction = pendingRequest(roleRoute(1, "A"));
        wrongAction.setActionType(ApprovalActionType.REJECT);

        assertThat(policy.validateRuntime(null).reason()).isEqualTo(UNSUPPORTED_TARGET_ACTION);
        assertThat(policy.validateRuntime(wrongTarget).reason()).isEqualTo(UNSUPPORTED_TARGET_ACTION);
        assertThat(policy.validateRuntime(wrongAction).reason()).isEqualTo(UNSUPPORTED_TARGET_ACTION);
    }

    @Test
    void decisionRequiresThePersistedCurrentStep() {
        UUID actor = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(roleRoute(1, "FIRST"), roleRoute(2, "SECOND"));
        ApprovalStep later = request.getSteps().get(1);

        assertThat(policy.validateDecision(request, null, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(CURRENT_STEP_ONLY);
        assertThat(policy.validateDecision(request, later, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(CURRENT_STEP_ONLY);
    }

    @Test
    void decisionRejectsForeignAndDeletedStepsWithTheCurrentStepNumber() {
        UUID actor = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(roleRoute(1, "APPROVER"));
        UUID activeStepId = UUID.randomUUID();
        request.getSteps().get(0).setId(activeStepId);
        ApprovalStep foreign = pendingRoleStep(1, "APPROVER");
        ApprovalStep deleted = pendingRoleStep(1, "APPROVER");
        deleted.setId(activeStepId);
        deleted.setDeleted(true);
        deleted.setRequest(request);
        request.getSteps().add(deleted);

        assertThat(policy.validateDecision(request, foreign, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(CURRENT_STEP_ONLY);
        assertThat(policy.validateDecision(request, deleted, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(CURRENT_STEP_ONLY);
    }

    @Test
    void decisionAcceptsAnotherEntityInstanceOnlyWhenPersistedIdsMatch() {
        UUID stepId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(roleRoute(1, "APPROVER"));
        request.getSteps().get(0).setId(stepId);
        ApprovalStep persistedRepresentation = pendingRoleStep(1, "APPROVER");
        persistedRepresentation.setId(stepId);

        assertThat(policy.validateDecision(request, persistedRepresentation, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(VALID);

        persistedRepresentation.setId(UUID.randomUUID());
        assertThat(policy.validateDecision(request, persistedRepresentation, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(CURRENT_STEP_ONLY);
    }

    @Test
    void decisionRequiresAnActorAndPersistedAssignmentEligibility() {
        ApprovalRequest request = pendingRequest(roleRoute(1, "APPROVER"));
        ApprovalStep current = request.getSteps().get(0);

        assertThat(policy.validateDecision(request, current, null,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(DECISION_ACTOR_MISSING);
        assertThat(policy.validateDecision(request, current, UUID.randomUUID(),
                ApprovalDecision.APPROVED, false).reason()).isEqualTo(ACTOR_INELIGIBLE);
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalDecision.class, names = {"APPROVED", "REJECTED"})
    void requesterCannotApproveOrReject(ApprovalDecision outcome) {
        UUID requester = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(requester, pendingRoleStep(1, "APPROVER"));

        assertThat(policy.validateDecision(request, request.getSteps().get(0), requester,
                outcome, true).reason()).isEqualTo(REQUESTER_DECISION);
    }

    @ParameterizedTest(name = "unsupported outcome: {0}")
    @MethodSource("unsupportedDecisionOutcomes")
    void decisionFailsClosedForOutcomesOtherThanApproveOrReject(ApprovalDecision outcome) {
        ApprovalRequest request = pendingRequest(roleRoute(1, "APPROVER"));

        assertThat(policy.validateDecision(request, request.getSteps().get(0), UUID.randomUUID(),
                outcome, true).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
    }

    private static Stream<Arguments> unsupportedDecisionOutcomes() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(ApprovalDecision.PENDING),
                Arguments.of(ApprovalDecision.RETURNED));
    }

    @Test
    void blocksSecondApproveBySameActorButAllowsEligibleReject() {
        UUID requester = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(requester,
                approvedStep(1, actor), pendingRoleStep(2, "APPROVER"));
        ApprovalStep current = request.getSteps().get(1);
        assertThat(policy.validateDecision(request, current, actor,
                ApprovalDecision.APPROVED, true).reason()).isEqualTo(REPEATED_APPROVING_ACTOR);
        assertThat(policy.validateDecision(request, current, actor,
                ApprovalDecision.REJECTED, true).reason()).isEqualTo(VALID);
    }

    @Test
    void eligibleDistinctActorMayApproveTheCurrentStep() {
        UUID actor = UUID.randomUUID();
        ApprovalRequest request = pendingRequest(roleRoute(1, "APPROVER"));

        ValidationResult result = policy.validateDecision(request, request.getSteps().get(0), actor,
                ApprovalDecision.APPROVED, true);

        assertThat(result.reason()).isEqualTo(VALID);
        assertThat(result.orderedSteps()).containsExactly(roleRoute(1, "APPROVER"));
    }

    @Test
    void decisionPropagatesRuntimeValidationBeforeActorChecks() {
        ApprovalRequest request = pendingRequest();

        assertThat(policy.validateDecision(request, null, null,
                ApprovalDecision.APPROVED, false).reason()).isEqualTo(EMPTY_ROUTE);
    }

    @Test
    void approvedRuntimeIsCompleteButNotActionable() {
        ApprovalRequest request = approvedRequest(approvedStep(1, UUID.randomUUID()));

        assertThat(policy.validateDecision(request, request.getSteps().get(0), UUID.randomUUID(),
                ApprovalDecision.REJECTED, true).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 11})
    void completesOnlyWhenAllRuntimeStepsAreApproved(int count) {
        ApprovalStep[] steps = IntStream.rangeClosed(1, count)
                .mapToObj(order -> approvedStep(order, UUID.randomUUID()))
                .toArray(ApprovalStep[]::new);

        ValidationResult result = policy.validateCompletion(approvedRequest(steps));

        assertThat(result.reason()).isEqualTo(VALID);
        assertThat(result.orderedSteps()).hasSize(count);
    }

    @Test
    void acceptsFinalStepApprovedBeforePendingRequestTransitionsToApproved() {
        ApprovalRequest request = request(
                ApprovalStatus.PENDING,
                UUID.randomUUID(),
                approvedStep(1, UUID.randomUUID()),
                approvedStep(2, UUID.randomUUID()));
        request.setCurrentStep(2);

        assertThat(policy.validateRuntime(request).reason()).isEqualTo(CURRENT_STEP_INVALID);
        assertThat(policy.validateCompletion(request).reason()).isEqualTo(VALID);
    }

    @Test
    void completionRequiresTheFinalCurrentStepEvenWhenEveryDecisionIsApproved() {
        ApprovalRequest request = request(
                ApprovalStatus.PENDING,
                UUID.randomUUID(),
                approvedStep(1, UUID.randomUUID()),
                approvedStep(2, UUID.randomUUID()));
        request.setCurrentStep(1);

        assertThat(policy.validateCompletion(request).reason()).isEqualTo(CURRENT_STEP_INVALID);
    }

    @Test
    void preTransitionCompletionReturnsPreciseDecisionEvidenceReasons() {
        UUID requester = UUID.randomUUID();
        ApprovalStep missingActor = pendingRoleStep(1, "FIRST");
        missingActor.setDecision(ApprovalDecision.APPROVED);
        ApprovalRequest request = request(ApprovalStatus.PENDING, requester, missingActor);
        request.setCurrentStep(1);

        assertThat(policy.validateCompletion(request).reason()).isEqualTo(DECISION_ACTOR_MISSING);

        missingActor.setDecidedById(requester);
        assertThat(policy.validateCompletion(request).reason()).isEqualTo(REQUESTER_DECISION);

        UUID actor = UUID.randomUUID();
        request = request(ApprovalStatus.PENDING, requester,
                approvedStep(1, actor), approvedStep(2, actor));
        request.setCurrentStep(2);
        assertThat(policy.validateCompletion(request).reason()).isEqualTo(REPEATED_APPROVING_ACTOR);
    }

    @Test
    void pendingRuntimeIsIncompleteForCompletion() {
        ApprovalRequest request = pendingRequest(UUID.randomUUID(),
                approvedStep(1, UUID.randomUUID()), pendingRoleStep(2, "SECOND"));

        assertThat(policy.validateCompletion(request).reason()).isEqualTo(RUNTIME_INCOMPLETE);
    }

    @Test
    void completionRejectsUnsupportedRequestStatusAfterCompleteEvidence() {
        ApprovalRequest request = request(
                ApprovalStatus.DRAFT, UUID.randomUUID(), approvedStep(1, UUID.randomUUID()));
        request.setCurrentStep(1);

        assertThat(policy.validateCompletion(request).reason()).isEqualTo(REQUEST_STATUS_INCONSISTENT);
    }

    @Test
    void completionPropagatesMalformedRuntimeReason() {
        ApprovalRequest request = pendingRequest(roleRoute(2, "SECOND"));

        assertThat(policy.validateCompletion(request).reason()).isEqualTo(NONCONTIGUOUS_ORDER);
    }

    private static ApprovalTemplate roleTemplate(int count, String role) {
        return template(IntStream.rangeClosed(1, count)
                .mapToObj(order -> roleRoute(order, role))
                .toArray(RouteStep[]::new));
    }

    private static ApprovalTemplate template(RouteStep... route) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        template.setActionType(ApprovalActionType.APPROVE);
        template.setActive(true);
        Arrays.stream(route).map(LifecycleApprovalRoutePolicyTest::templateStep)
                .forEach(template.getSteps()::add);
        return template;
    }

    private static ApprovalTemplateStep templateStep(RouteStep routeStep) {
        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setStepOrder(routeStep.order());
        step.setApproverId(routeStep.approverId());
        step.setApproverRole(routeStep.approverRole());
        return step;
    }

    private static ApprovalRequest pendingRequest(RouteStep... route) {
        ApprovalStep[] steps = Arrays.stream(route)
                .map(LifecycleApprovalRoutePolicyTest::runtimeStep)
                .toArray(ApprovalStep[]::new);
        return pendingRequest(UUID.randomUUID(), steps);
    }

    private static ApprovalRequest pendingRequest(UUID requester, ApprovalStep... steps) {
        ApprovalRequest request = request(ApprovalStatus.PENDING, requester, steps);
        request.setCurrentStep(steps.length == 0 ? 1 : firstPendingOrder(steps));
        return request;
    }

    private static ApprovalRequest approvedRequest(ApprovalStep... steps) {
        ApprovalRequest request = request(ApprovalStatus.APPROVED, UUID.randomUUID(), steps);
        request.setCurrentStep(steps.length);
        return request;
    }

    private static ApprovalRequest request(ApprovalStatus status, UUID requester, ApprovalStep... steps) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setRequesterId(requester);
        request.setStatus(status);
        request.getSteps().addAll(Arrays.asList(steps));
        stepsWithValues(steps).forEach(step -> step.setRequest(request));
        return request;
    }

    private static int firstPendingOrder(ApprovalStep[] steps) {
        return stepsWithValues(steps)
                .filter(step -> step.getDecision() == ApprovalDecision.PENDING)
                .mapToInt(ApprovalStep::getStepNumber)
                .min()
                .orElse(stepsWithValues(steps).mapToInt(ApprovalStep::getStepNumber).max().orElse(1));
    }

    private static Stream<ApprovalStep> stepsWithValues(ApprovalStep[] steps) {
        return Arrays.stream(steps).filter(java.util.Objects::nonNull);
    }

    private static ApprovalStep approvedStep(int order, UUID actor) {
        ApprovalStep step = pendingRoleStep(order, "ROLE_" + order);
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(actor);
        return step;
    }

    private static ApprovalStep pendingStep(int order, RouteStep assignment) {
        ApprovalStep step = runtimeStep(new RouteStep(
                order, assignment.approverId(), assignment.approverRole()));
        step.setDecision(ApprovalDecision.PENDING);
        return step;
    }

    private static ApprovalStep pendingRoleStep(int order, String role) {
        return pendingStep(order, roleRoute(order, role));
    }

    private static ApprovalStep runtimeStep(RouteStep routeStep) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(routeStep.order());
        step.setApproverId(routeStep.approverId());
        step.setApproverRole(routeStep.approverRole());
        step.setDecision(ApprovalDecision.PENDING);
        return step;
    }

    private static RouteStep roleRoute(int order, String role) {
        return new RouteStep(order, null, role);
    }

    private static RouteStep explicitRoute(int order, UUID approver) {
        return new RouteStep(order, approver, null);
    }

    private static RouteStep[] routes(RouteStep... route) {
        return route;
    }
}
