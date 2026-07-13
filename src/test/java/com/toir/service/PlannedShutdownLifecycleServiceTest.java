package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownRescheduleRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownExtensionRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownTransitionRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownBlocker;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory;
import com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.*;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.*;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.plannedshutdown.*;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownLifecycleServiceTest {
    @Mock PlannedShutdownRepository repository;
    @Mock PlannedShutdownAssetRepository assetRepository;
    @Mock PlannedShutdownWorkItemRepository workItemRepository;
    @Mock PlannedShutdownReadinessItemRepository readinessItemRepository;
    @Mock PlannedShutdownIsolationPointRepository isolationPointRepository;
    @Mock PlannedShutdownStatusHistoryRepository historyRepository;
    @Mock ApprovalRequestRepository approvalRequestRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock DefectRepository defectRepository;
    @Mock PprTaskRepository pprTaskRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock com.toir.service.repair.CanonicalWorkSourceResolver canonicalWorkSourceResolver;
    @Mock WorkOrderService workOrderService;
    @Mock WorkOrderMaterialReadinessService materialReadinessService;
    @Mock WorkOrderAssignmentEligibilityService assignmentEligibilityService;
    @Mock SafetyPermitRepository safetyPermitRepository;
    @Mock PlannedShutdownWorkItemPolicy workItemPolicy;
    @Mock PlannedShutdownReadinessPolicy readinessPolicy;
    @Mock PlannedShutdownReadinessLifecyclePolicy readinessLifecyclePolicy;
    PlannedShutdownTransitionPolicy transitionPolicy = new PlannedShutdownTransitionPolicy();
    PlannedShutdownApprovalScopeHasher approvalScopeHasher = new PlannedShutdownApprovalScopeHasher();
    @Mock PlannedShutdownEvidenceService evidenceService;
    @Mock PlannedShutdownReportService reportService;
    @Mock ScopeAccessService scopeAccessService;
    @Mock AuditBuilderService audit;

    PlannedShutdownService service;
    UUID id;
    UUID actor;
    PlannedShutdown shutdown;

    @BeforeEach
    void setUp() {
        service = new PlannedShutdownService(repository, assetRepository, workItemRepository,
                readinessItemRepository, isolationPointRepository, historyRepository, approvalRequestRepository,
                departmentRepository, employeeRepository, equipmentRepository, defectRepository, pprTaskRepository,
                workOrderRepository, canonicalWorkSourceResolver, workOrderService, materialReadinessService, assignmentEligibilityService, safetyPermitRepository,
                workItemPolicy, readinessPolicy, readinessLifecyclePolicy, transitionPolicy, approvalScopeHasher,
                evidenceService, reportService, scopeAccessService, audit);
        id = UUID.randomUUID();
        actor = UUID.randomUUID();
        shutdown = new PlannedShutdown();
        shutdown.setId(id);
        shutdown.setVersion(7L);
        shutdown.setScopeVersion(3L);
        shutdown.setWindowVersion(2L);
        shutdown.setPlannedStartAt(Instant.parse("2026-07-12T01:00:00Z"));
        shutdown.setPlannedEndAt(Instant.parse("2026-07-12T05:00:00Z"));
        lenient().when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(Optional.of(shutdown));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(historyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(scopeAccessService.currentUserIdOrNull()).thenReturn(actor);
        lenient().when(scopeAccessService.currentEmployeeId()).thenReturn(Optional.of(actor));
        lenient().when(scopeAccessService.canAccessDepartment(any())).thenReturn(true);
        lenient().when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        lenient().when(workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        lenient().when(readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        lenient().when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        lenient().when(readinessPolicy.evaluateReadiness(any())).thenReturn(
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(true, List.of()));
        lenient().when(readinessPolicy.evaluateSafeState(any())).thenReturn(
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(true, List.of()));
    }

    @Test
    void lockedForwardTransitionWritesCanonicalHistoryAndServerTimestampOnce() {
        shutdown.setStatus(PlannedShutdownStatus.SAFE_STATE);

        var response = service.startRepair(id, new PlannedShutdownTransitionRequest(7L, "safe handoff", "corr-1"));

        assertThat(response.status()).isEqualTo(PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        assertThat(response.actualRepairStartAt()).isNotNull();
        ArgumentCaptor<PlannedShutdownStatusHistory> history = ArgumentCaptor.forClass(PlannedShutdownStatusHistory.class);
        verify(historyRepository).saveAndFlush(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(PlannedShutdownStatus.SAFE_STATE);
        assertThat(history.getValue().getToStatus()).isEqualTo(PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        assertThat(history.getValue().getActorId()).isEqualTo(actor);
        assertThat(history.getValue().getCorrelationKey()).isEqualTo("corr-1");

        Instant first = shutdown.getActualRepairStartAt();
        assertThatThrownBy(() -> service.startRepair(id,
                new PlannedShutdownTransitionRequest(7L, "repeat", "corr-2")))
                .hasMessageContaining("TRANSITION_NOT_ALLOWED");
        assertThat(shutdown.getActualRepairStartAt()).isEqualTo(first);
    }

    @Test
    void rescheduleInvalidatesApprovalAndReturnsToReadinessWithNewEffectiveWindow() {
        shutdown.setStatus(PlannedShutdownStatus.APPROVED);
        shutdown.setApprovalScopeVersion(3L);
        shutdown.setApprovalScopeHash("old-hash");
        shutdown.setApprovedStartAt(shutdown.getPlannedStartAt());
        shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        Instant newStart = Instant.parse("2026-07-13T01:00:00Z");
        Instant newEnd = Instant.parse("2026-07-13T07:00:00Z");

        var response = service.reschedule(id,
                new PlannedShutdownRescheduleRequest(7L, newStart, newEnd, "production conflict", "rs-1"));

        assertThat(response.status()).isEqualTo(PlannedShutdownStatus.READINESS_CHECK);
        assertThat(response.scopeVersion()).isEqualTo(4L);
        assertThat(response.windowVersion()).isEqualTo(3L);
        assertThat(response.approvalScopeVersion()).isNull();
        assertThat(response.approvalScopeHash()).isNull();
        assertThat(response.plannedStartAt()).isEqualTo(newStart);
        assertThat(response.plannedEndAt()).isEqualTo(newEnd);
        ArgumentCaptor<PlannedShutdownStatusHistory> history = ArgumentCaptor.forClass(PlannedShutdownStatusHistory.class);
        verify(historyRepository, times(2)).saveAndFlush(history.capture());
        assertThat(history.getAllValues()).extracting(PlannedShutdownStatusHistory::getFromStatus,
                        PlannedShutdownStatusHistory::getToStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PlannedShutdownStatus.APPROVED, PlannedShutdownStatus.RESCHEDULED),
                        org.assertj.core.groups.Tuple.tuple(PlannedShutdownStatus.RESCHEDULED, PlannedShutdownStatus.READINESS_CHECK));
    }

    @Test
    void reachablePreApprovalCommandsCaptureDeterministicScopeSnapshot() {
        shutdown.setStatus(PlannedShutdownStatus.DRAFT);
        service.formScope(id, new PlannedShutdownTransitionRequest(7L, "scope", "s-1"));
        service.beginReadiness(id, new PlannedShutdownTransitionRequest(7L, "ready", "s-2"));
        when(readinessPolicy.evaluateReadiness(any())).thenReturn(
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(true, List.of()));

        var response = service.requestApproval(id,
                new PlannedShutdownTransitionRequest(7L, "submit", "s-3"));

        assertThat(response.status()).isEqualTo(PlannedShutdownStatus.PENDING_APPROVAL);
        assertThat(response.approvalScopeVersion()).isEqualTo(3L);
        assertThat(response.approvalScopeHash()).matches("[0-9a-f]{64}");
        assertThat(response.approvedStartAt()).isEqualTo(response.plannedStartAt());
        assertThat(response.approvedEndAt()).isEqualTo(response.plannedEndAt());
    }

    @Test
    void executesEveryCurrentlyReachableLegalServiceEdgeThroughStartup() {
        shutdown.setStatus(PlannedShutdownStatus.DRAFT);
        var proceed = new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(true, List.of());
        when(readinessPolicy.evaluateReadiness(any())).thenReturn(proceed);
        when(readinessPolicy.evaluateSafeState(any())).thenReturn(proceed);
        service.formScope(id, command());
        service.beginReadiness(id, command());
        var pending = service.requestApproval(id, command());
        ApprovalRequest approval = approval(actor, UUID.randomUUID(), UUID.randomUUID());
        approval.setPayloadJson("{\"scopeVersion\":3,\"scopeHash\":\"" + pending.approvalScopeHash() + "\"}");
        service.finalizeApprovalFromApprovalRequest(id, approval);
        when(approvalRequestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(anyString(), eq(id)))
                .thenReturn(List.of(approval));

        service.prepare(id, command());
        service.startShutdown(id, command());
        service.confirmSafeState(id, command());
        service.startRepair(id, command());
        service.startTesting(id, command());

        assertThat(shutdown.getLifecycleStatus()).isEqualTo(PlannedShutdownStatus.TESTING);
        assertThat(shutdown.getActualShutdownAt()).isNotNull();
        assertThat(shutdown.getActualSafeStateAt()).isNotNull();
        assertThat(shutdown.getActualRepairStartAt()).isNotNull();
        assertThat(shutdown.getActualTestingStartAt()).isNotNull();
        assertThat(service.startStartup(id, command()).status()).isEqualTo(PlannedShutdownStatus.STARTUP);
        assertThat(shutdown.getActualStartupAt()).isNotNull();
    }

    @Test
    void emergencyExtensionRecordsEventAndReturnsToOperationalStatus() {
        shutdown.setStatus(PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        shutdown.setApprovedStartAt(shutdown.getPlannedStartAt());
        shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        Instant extended = shutdown.getPlannedEndAt().plusSeconds(3600);

        var response = service.extend(id, new PlannedShutdownExtensionRequest(7L, extended, "unexpected work", "e-1"));

        assertThat(response.status()).isEqualTo(PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        assertThat(response.effectiveExtensionEndAt()).isEqualTo(extended);
        assertThat(response.windowVersion()).isEqualTo(3L);
        ArgumentCaptor<PlannedShutdownStatusHistory> history = ArgumentCaptor.forClass(PlannedShutdownStatusHistory.class);
        verify(historyRepository, times(2)).saveAndFlush(history.capture());
        assertThat(history.getAllValues()).extracting(PlannedShutdownStatusHistory::getFromStatus,
                        PlannedShutdownStatusHistory::getToStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PlannedShutdownStatus.REPAIR_IN_PROGRESS,
                                PlannedShutdownStatus.EMERGENCY_EXTENDED),
                        org.assertj.core.groups.Tuple.tuple(PlannedShutdownStatus.EMERGENCY_EXTENDED,
                                PlannedShutdownStatus.REPAIR_IN_PROGRESS));
    }

    @Test
    void auditedExtensionKeepsBaseApprovalsCurrentForSafeStateAndRepair() {
        shutdown.setStatus(PlannedShutdownStatus.SHUTDOWN_STARTED);
        shutdown.setApprovedStartAt(shutdown.getPlannedStartAt());
        shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        shutdown.setApprovalScopeVersion(shutdown.getScopeVersion());
        shutdown.setApprovalScopeHash(currentScopeHash());
        ApprovalRequest approval = approval(actor, UUID.randomUUID(), UUID.randomUUID());
        when(approvalRequestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(anyString(), eq(id)))
                .thenReturn(List.of(approval));
        Instant extendedEnd = shutdown.getApprovedEndAt().plusSeconds(1800);
        var proceed = new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(true, List.of());
        when(readinessPolicy.evaluateSafeState(any())).thenAnswer(invocation -> {
            PlannedShutdownReadinessPolicy.Facts facts = invocation.getArgument(0);
            assertThat(facts.productionApproved()).isTrue();
            assertThat(facts.hseApproved()).isTrue();
            assertThat(facts.approvalScopeCurrent()).isTrue();
            assertThat(facts.approvedEndAt()).isEqualTo(extendedEnd);
            return proceed;
        });

        service.extend(id, new PlannedShutdownExtensionRequest(7L, extendedEnd,
                "production stabilization", "ext-current"));
        service.confirmSafeState(id, command());
        service.startRepair(id, command());

        assertThat(shutdown.getLifecycleStatus()).isEqualTo(PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        verify(readinessPolicy, times(4)).evaluateSafeState(any());
    }

    @Test
    void cancellationRequiresReasonAndRecordsTerminalTransition() {
        shutdown.setStatus(PlannedShutdownStatus.PREPARATION);
        assertThatThrownBy(() -> service.cancel(id, new PlannedShutdownTransitionRequest(7L, " ", "c-0")))
                .hasMessageContaining("CANCEL_REASON_REQUIRED");
        var response = service.cancel(id, new PlannedShutdownTransitionRequest(7L, "weather", "c-1"));
        assertThat(response.status()).isEqualTo(PlannedShutdownStatus.CANCELLED);
        verify(historyRepository).saveAndFlush(argThat(h -> h.getFromStatus() == PlannedShutdownStatus.PREPARATION
                && h.getToStatus() == PlannedShutdownStatus.CANCELLED));
    }

    @Test
    void cancellationIsBlockedWhileLinkedWorkOrdersRemainActive() {
        shutdown.setStatus(PlannedShutdownStatus.PREPARATION);
        when(workOrderRepository.existsActiveByPlannedShutdownId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(id,
                new PlannedShutdownTransitionRequest(7L, "weather", "c-active")))
                .hasMessageContaining("CANCEL_ACTIVE_WORK_ORDERS");
        verify(historyRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvalFinalizationRequiresCurrentProductionAndHseStepsWithSeparationOfDuty() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        shutdown.setApprovalScopeVersion(3L);
        shutdown.setApprovalScopeHash(currentScopeHash());
        ApprovalRequest approval = approval(actor, UUID.randomUUID(), UUID.randomUUID());

        var result = service.finalizeApprovalFromApprovalRequest(id, approval);

        assertThat(result.status()).isEqualTo(PlannedShutdownStatus.APPROVED);
        verify(repository).findByIdAndIsDeletedFalseForUpdate(id);
    }

    @Test
    void approvalFinalizationRejectsStaleScopeAndRequesterSelfApproval() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        shutdown.setApprovalScopeVersion(2L);
        ApprovalRequest approval = approval(actor, UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> service.finalizeApprovalFromApprovalRequest(id, approval))
                .hasMessageContaining("APPROVAL_SCOPE_STALE");

        shutdown.setApprovalScopeVersion(3L);
        shutdown.setApprovalScopeHash(currentScopeHash());
        ApprovalRequest selfApproved = approval(actor, actor, UUID.randomUUID());
        assertThatThrownBy(() -> service.finalizeApprovalFromApprovalRequest(id, selfApproved))
                .hasMessageContaining("APPROVAL_PRODUCTION_MISSING");
    }

    @Test
    void approvalRejectsSubstringRoleAndSameActorAcrossProductionAndHse() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        shutdown.setApprovalScopeVersion(3L);
        shutdown.setApprovalScopeHash(currentScopeHash());
        ApprovalRequest substring = approval(actor, UUID.randomUUID(), UUID.randomUUID());
        substring.getSteps().getFirst().setApproverRole("FAKE_"
                + PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE);
        assertThatThrownBy(() -> service.finalizeApprovalFromApprovalRequest(id, substring))
                .hasMessageContaining("APPROVAL_PRODUCTION_MISSING");

        UUID sameActor = UUID.randomUUID();
        ApprovalRequest same = approval(actor, sameActor, sameActor);
        assertThatThrownBy(() -> service.finalizeApprovalFromApprovalRequest(id, same))
                .hasMessageContaining("APPROVAL_SEPARATION_OF_DUTY_REQUIRED");
    }

    @Test
    void approvalFinalizationRecomputesCurrentOperationalScopeHash() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        shutdown.setApprovalScopeVersion(3L);
        shutdown.setApprovedStartAt(shutdown.getPlannedStartAt());
        shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        String approvedHash = approvalScopeHasher.hash(shutdown, List.of(), List.of(), List.of(), List.of());
        shutdown.setApprovalScopeHash(approvedHash);
        ApprovalRequest approval = approval(actor, UUID.randomUUID(), UUID.randomUUID());
        approval.setPayloadJson("{\"scopeVersion\":3,\"scopeHash\":\"" + approvedHash + "\"}");
        shutdown.setObjective("mutated after approval request");

        assertThatThrownBy(() -> service.finalizeApprovalFromApprovalRequest(id, approval))
                .hasMessageContaining("APPROVAL_SCOPE_STALE");
    }

    @Test
    void taskSevenTransitionsConsumeCurrentEvidenceAndCreateOneSnapshot() {
        shutdown.setStatus(PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        when(workOrderRepository.existsActiveByPlannedShutdownId(id)).thenReturn(true);
        assertThatThrownBy(() -> service.startTesting(id, command()))
                .hasMessageContaining("TESTING_ACTIVE_WORK_ORDERS");

        when(workOrderRepository.existsActiveByPlannedShutdownId(id)).thenReturn(false);
        shutdown.setStatus(PlannedShutdownStatus.TESTING);
        assertThat(service.startStartup(id, command()).status()).isEqualTo(PlannedShutdownStatus.STARTUP);
        assertThat(service.complete(id, command()).status()).isEqualTo(PlannedShutdownStatus.COMPLETED);
        assertThat(service.close(id, command()).status()).isEqualTo(PlannedShutdownStatus.CLOSED);
        verify(evidenceService, times(5)).startupBlockers(id);
        verify(evidenceService, times(4)).productionReturnBlockers(id, 3L, 2L);
        verify(reportService, times(2)).closureBlockers(id);
        verify(reportService).createSnapshot(shutdown, actor);
        assertThatThrownBy(() -> service.close(id, command())).hasMessageContaining("TRANSITION_NOT_ALLOWED");
    }

    @Test
    void currentStartupAndProductionEvidenceFailClosed() {
        shutdown.setStatus(PlannedShutdownStatus.TESTING);
        when(evidenceService.startupBlockers(id)).thenReturn(List.of(new PlannedShutdownBlocker(
                "STARTUP_TEST_FAILED", "failed", "STARTUP_TEST", UUID.randomUUID())));
        assertThatThrownBy(() -> service.startStartup(id, command())).hasMessageContaining("STARTUP_TEST_FAILED");

        reset(evidenceService);
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        when(evidenceService.productionReturnBlockers(id, 3L, 2L)).thenReturn(List.of(new PlannedShutdownBlocker(
                "PRODUCTION_RETURN_MISSING", "missing", "PLANNED_SHUTDOWN", id)));
        assertThatThrownBy(() -> service.complete(id, command())).hasMessageContaining("PRODUCTION_RETURN_MISSING");
    }

    @Test
    void transitionEvidenceFailuresAreTypedVersionedBlockers() {
        shutdown.setStatus(PlannedShutdownStatus.TESTING);
        PlannedShutdownBlocker failedTest = new PlannedShutdownBlocker(
                "STARTUP_TEST_FAILED", "Mandatory test failed", "STARTUP_TEST", UUID.randomUUID());
        when(evidenceService.startupBlockers(id)).thenReturn(List.of(failedTest));

        assertThatThrownBy(() -> service.startStartup(id, command()))
                .isInstanceOfSatisfying(com.toir.exception.PlannedShutdownBlockerException.class, ex -> {
                    assertThat(ex.getVersion()).isEqualTo(7L);
                    assertThat(ex.getBlockers()).containsExactly(failedTest);
                });

        reset(evidenceService);
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        PlannedShutdownBlocker staleReturn = new PlannedShutdownBlocker(
                "PRODUCTION_RETURN_STALE", "Production return is stale", "PRODUCTION_RETURN", UUID.randomUUID());
        when(evidenceService.productionReturnBlockers(id, 3L, 2L)).thenReturn(List.of(staleReturn));
        assertThatThrownBy(() -> service.complete(id, command()))
                .isInstanceOfSatisfying(com.toir.exception.PlannedShutdownBlockerException.class,
                        ex -> assertThat(ex.getBlockers()).containsExactly(staleReturn));

        reset(evidenceService, reportService);
        shutdown.setStatus(PlannedShutdownStatus.COMPLETED);
        PlannedShutdownBlocker existingSnapshot = new PlannedShutdownBlocker(
                "CLOSURE_SNAPSHOT_ALREADY_EXISTS", "Snapshot exists", "PLANNED_SHUTDOWN_CLOSURE_SNAPSHOT",
                UUID.randomUUID());
        when(reportService.closureBlockers(id)).thenReturn(List.of(existingSnapshot));
        assertThatThrownBy(() -> service.close(id, command()))
                .isInstanceOfSatisfying(com.toir.exception.PlannedShutdownBlockerException.class,
                        ex -> assertThat(ex.getBlockers()).containsExactly(existingSnapshot));
    }

    @Test
    void startupExtensionRequiresCurrentReapprovalBeforeCompletion() {
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        Instant extendedEnd = shutdown.getPlannedEndAt().plusSeconds(1800);
        service.extend(id, new PlannedShutdownExtensionRequest(7L, extendedEnd, "startup stabilization", "ext-1"));
        assertThat(shutdown.getWindowVersion()).isEqualTo(3L);

        var current = new com.toir.dto.plannedshutdown.PlannedShutdownProductionReturnResponse(
                UUID.randomUUID(), id, 3L, 3L, actor, Instant.now(), "extended window stable");
        when(evidenceService.productionReturnBlockers(id, 3L, 3L))
                .thenReturn(List.of(new PlannedShutdownBlocker(
                        "PRODUCTION_RETURN_STALE", "stale", "PRODUCTION_RETURN", current.id())))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.complete(id, command()))
                .hasMessageContaining("PRODUCTION_RETURN_STALE");

        var request = new com.toir.dto.plannedshutdown.PlannedShutdownProductionReturnRequest(
                7L, "extended window stable");
        when(evidenceService.approveProductionReturn(id, PlannedShutdownStatus.STARTUP, 3L, 3L, request, actor))
                .thenReturn(current);
        assertThat(service.approveProductionReturn(id, request).windowVersion()).isEqualTo(3L);
        assertThat(service.complete(id, command()).status()).isEqualTo(PlannedShutdownStatus.COMPLETED);
    }

    @Test
    void completionAndCloseReportActiveWorkAndUnreleasedIsolationBeforeTaskSevenEvidence() {
        var point = new com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint();
        point.setAppliedAt(Instant.now());
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        when(workOrderRepository.existsActiveByPlannedShutdownId(id)).thenReturn(true, false, true, false);
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(point));
        assertThatThrownBy(() -> service.complete(id, command()))
                .hasMessageContaining("COMPLETION_ACTIVE_WORK_ORDERS");
        assertThatThrownBy(() -> service.complete(id, command()))
                .hasMessageContaining("COMPLETION_ISOLATION_UNRELEASED");
        shutdown.setStatus(PlannedShutdownStatus.COMPLETED);
        assertThatThrownBy(() -> service.close(id, command()))
                .hasMessageContaining("CLOSE_ACTIVE_WORK_ORDERS");
        assertThatThrownBy(() -> service.close(id, command()))
                .hasMessageContaining("CLOSE_ISOLATION_UNRELEASED");
    }

    @Test
    void completionFailsClosedForDefinedIsolationThatWasNeverAppliedOrReleased() {
        PlannedShutdownIsolationPoint point = isolationPoint(1);
        point.setAppliedAt(null);
        point.setVerifiedAt(null);
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(point));

        assertThatThrownBy(() -> service.complete(id, command()))
                .hasMessageContaining("COMPLETION_ISOLATION_UNRELEASED");
        verify(evidenceService, never()).productionReturnBlockers(any(), any(), any());
    }

    @Test
    void isolationReleaseFollowsConfiguredOrder() {
        shutdown.setStatus(PlannedShutdownStatus.STARTUP);
        PlannedShutdownIsolationPoint first = isolationPoint(1);
        PlannedShutdownIsolationPoint second = isolationPoint(2);
        when(readinessLifecyclePolicy.canReleaseIsolation(PlannedShutdownStatus.STARTUP)).thenReturn(true);
        when(isolationPointRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(second.getId(), id))
                .thenReturn(Optional.of(second));
        when(isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.releaseIsolation(id, second.getId(),
                new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(7L)))
                .hasMessageContaining("ISOLATION_RELEASE_ORDER_VIOLATION");

        first.setReleasedAt(Instant.now());
        assertThat(service.releaseIsolation(id, second.getId(),
                new com.toir.dto.plannedshutdown.PlannedShutdownIsolationActionRequest(7L)).points())
                .anyMatch(point -> point.id().equals(second.getId()) && point.releasedAt() != null);
    }

    @Test
    void repairStartConsumesCurrentSafeAssessment() {
        shutdown.setStatus(PlannedShutdownStatus.SAFE_STATE);
        when(readinessPolicy.evaluateSafeState(any())).thenReturn(
                new com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment(false, List.of(
                        new com.toir.dto.plannedshutdown.PlannedShutdownBlocker(
                                "ISOLATION_NOT_VERIFIED", "not verified", "ISOLATION", id))));
        assertThatThrownBy(() -> service.startRepair(id, command()))
                .hasMessageContaining("REPAIR_START_BLOCKED:ISOLATION_NOT_VERIFIED");
    }

    private PlannedShutdownTransitionRequest command() {
        return new PlannedShutdownTransitionRequest(7L, "reason", UUID.randomUUID().toString());
    }

    private PlannedShutdownIsolationPoint isolationPoint(int order) {
        PlannedShutdownIsolationPoint point = new PlannedShutdownIsolationPoint();
        point.setId(UUID.randomUUID());
        point.setPlannedShutdownId(id);
        point.setEquipmentId(UUID.randomUUID());
        point.setIsolationMethod("LOTO");
        point.setLockTagIdentifier("TAG-" + order);
        point.setResponsibleEmployeeId(actor);
        point.setOrderNumber(order);
        point.setAppliedAt(Instant.now());
        point.setVerifiedAt(Instant.now());
        return point;
    }

    private ApprovalRequest approval(UUID requester, UUID productionActor, UUID hseActor) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(UUID.randomUUID());
        request.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        request.setTargetId(id);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setRequesterId(requester);
        request.setStatus(ApprovalStatus.APPROVED);
        request.setPayloadJson("{\"scopeVersion\":3,\"scopeHash\":\"" + shutdown.getApprovalScopeHash() + "\"}");
        request.setSteps(List.of(step(request, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE, productionActor),
                step(request, PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE, hseActor)));
        return request;
    }

    private ApprovalStep step(ApprovalRequest request, String role, UUID decidedBy) {
        ApprovalStep step = new ApprovalStep();
        step.setRequest(request);
        step.setStepNumber(role.equals(PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE) ? 1 : 2);
        step.setApproverRole(role);
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(decidedBy);
        return step;
    }

    private String currentScopeHash() {
        return approvalScopeHasher.hash(shutdown, List.of(), List.of(), List.of(), List.of());
    }
}
