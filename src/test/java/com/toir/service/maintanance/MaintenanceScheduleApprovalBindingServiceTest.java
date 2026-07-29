package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.PprPlan;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalResolutionCode;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import com.toir.service.approval.ApprovalRouteSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleApprovalBindingServiceTest {

    private static final String HASH = "a".repeat(64);

    @Mock
    private PprPlanRepository planRepository;
    @Mock
    private ApprovalRequestRepository requestRepository;
    @Mock
    private MaintenanceScheduleCalculationItemRepository itemRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private MaintenanceScheduleApprovalBindingService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleApprovalBindingService(
                planRepository, requestRepository, itemRepository, jdbcTemplate);
    }

    @Test
    void bindsApprovalToExactCurrentPlanRevisionHashAndVersion() {
        UUID planId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        PprPlan plan = approvalFirstPlan(planId, 3L, HASH);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.countByPlanIdAndCalculationRevision(planId, 3L))
                .thenReturn(4L);

        MaintenanceScheduleApprovalBinding binding = service.resolveForSubmission(
                ApprovalTargetType.PPR_PLAN,
                planId,
                ApprovalActionType.APPROVE,
                requesterId);

        assertThat(binding.planId()).isEqualTo(planId);
        assertThat(binding.calculationRevision()).isEqualTo(3L);
        assertThat(binding.calculationContentHash()).isEqualTo(HASH);
        assertThat(binding.calculationContentHashVersion()).isEqualTo(1);
        assertThat(binding.requesterContextFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void approvalFirstPlanWithMissingSnapshotNeverFallsBackToLegacy() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = approvalFirstPlan(planId, 2L, HASH);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.countByPlanIdAndCalculationRevision(planId, 2L))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.resolveForSubmission(
                ApprovalTargetType.PPR_PLAN,
                planId,
                ApprovalActionType.APPROVE,
                UUID.randomUUID()))
                .isInstanceOfSatisfying(RestException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo("PPR_CALCULATION_SNAPSHOT_MISSING"));
    }

    @Test
    void resolvedRouteRequiresApprovalTemplateAndPersistsFingerprints() {
        ApprovalRequest request = approvalRequest(UUID.randomUUID(), 5L, HASH);
        UUID templateId = UUID.randomUUID();
        ApprovalRouteSnapshot route = new ApprovalRouteSnapshot(
                com.toir.enums.ApprovalFlowType.SEQUENTIAL,
                templateId,
                7L,
                List.of(new com.toir.dto.approval.CreateApprovalRequest.StepInput(
                        UUID.randomUUID(), null)));

        service.bindResolvedRoute(request, route, route.steps());

        assertThat(request.getTemplateId()).isEqualTo(templateId);
        assertThat(request.getTemplateVersion()).isEqualTo(7L);
        assertThat(request.getResolvedRouteFingerprint()).matches("[0-9a-f]{64}");
        assertThat(request.getRequesterContextFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void exactTupleMatchUsesPlanRevisionHashAndVersionNotSubmittingActor() {
        UUID planId = UUID.randomUUID();
        ApprovalRequest request = approvalRequest(planId, 5L, HASH);
        request.setRequesterContextFingerprint("old-requester");
        MaintenanceScheduleApprovalBinding binding =
                new MaintenanceScheduleApprovalBinding(
                        planId,
                        5L,
                        HASH,
                        1,
                        "new-requester");

        assertThat(service.matches(request, binding)).isTrue();

        request.setCalculationContentHashVersion(2);
        assertThat(service.matches(request, binding)).isFalse();
    }

    @Test
    void recalculationSupersedesOldPendingRequestAndCancelsPendingActions() {
        UUID planId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApprovalRequest old = approvalRequest(planId, 1L, "b".repeat(64));
        ApprovalStep pending = new ApprovalStep();
        pending.setDecision(ApprovalDecision.PENDING);
        old.getSteps().add(pending);
        when(requestRepository.findAllPendingByTargetAndAction(
                "PPR_PLAN", planId, "APPROVE", "PENDING"))
                .thenReturn(List.of(old));

        service.supersedeForNewRevision(planId, 2L, HASH, 1, actorId);

        assertThat(old.getStatus()).isEqualTo(ApprovalStatus.SUPERSEDED);
        assertThat(old.getResolutionCode()).isEqualTo(ApprovalResolutionCode.NEW_REVISION);
        assertThat(old.getFailureReason()).isEqualTo("PPR_APPROVAL_SUPERSEDED_NEW_REVISION");
        assertThat(pending.getDecision()).isEqualTo(ApprovalDecision.CANCELLED);
        verify(requestRepository).saveAllAndFlush(List.of(old));
    }

    private static PprPlan approvalFirstPlan(UUID id, long revision, String hash) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.setCalculationRevision(revision);
        plan.setCalculationContentHash(hash);
        plan.setCalculationContentHashVersion(1);
        return plan;
    }

    private static ApprovalRequest approvalRequest(
            UUID planId, long revision, String hash) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.PPR_PLAN);
        request.setTargetId(planId);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setRequesterId(UUID.randomUUID());
        request.setStatus(ApprovalStatus.PENDING);
        request.setCalculationRevision(revision);
        request.setCalculationContentHash(hash);
        request.setCalculationContentHashVersion(1);
        return request;
    }
}
