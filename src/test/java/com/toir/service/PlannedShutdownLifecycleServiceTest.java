package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownRescheduleRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownTransitionRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory;
import com.toir.enums.*;
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
    @Mock WorkOrderMaterialReadinessService materialReadinessService;
    @Mock WorkOrderAssignmentEligibilityService assignmentEligibilityService;
    @Mock SafetyPermitRepository safetyPermitRepository;
    @Mock PlannedShutdownWorkItemPolicy workItemPolicy;
    @Mock PlannedShutdownReadinessPolicy readinessPolicy;
    @Mock PlannedShutdownReadinessLifecyclePolicy readinessLifecyclePolicy;
    PlannedShutdownTransitionPolicy transitionPolicy = new PlannedShutdownTransitionPolicy();
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
                workOrderRepository, materialReadinessService, assignmentEligibilityService, safetyPermitRepository,
                workItemPolicy, readinessPolicy, readinessLifecyclePolicy, transitionPolicy, scopeAccessService, audit);
        id = UUID.randomUUID();
        actor = UUID.randomUUID();
        shutdown = new PlannedShutdown();
        shutdown.setId(id);
        shutdown.setVersion(7L);
        shutdown.setScopeVersion(3L);
        shutdown.setPlannedStartAt(Instant.parse("2026-07-12T01:00:00Z"));
        shutdown.setPlannedEndAt(Instant.parse("2026-07-12T05:00:00Z"));
        lenient().when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(Optional.of(shutdown));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(historyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(scopeAccessService.currentUserIdOrNull()).thenReturn(actor);
        lenient().when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
        lenient().when(workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id))
                .thenReturn(List.of());
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
        assertThat(response.approvalScopeVersion()).isNull();
        assertThat(response.approvalScopeHash()).isNull();
        assertThat(response.plannedStartAt()).isEqualTo(newStart);
        assertThat(response.plannedEndAt()).isEqualTo(newEnd);
    }

    @Test
    void approvalFinalizationRequiresCurrentProductionAndHseStepsWithSeparationOfDuty() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        shutdown.setApprovalScopeVersion(3L);
        ApprovalRequest approval = approval(actor, UUID.randomUUID(), UUID.randomUUID());

        var result = service.finalizeApprovalFromApprovalRequest(id, approval);

        assertThat(result.status()).isEqualTo(PlanStatus.APPROVED);
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
        ApprovalRequest selfApproved = approval(actor, actor, UUID.randomUUID());
        assertThatThrownBy(() -> service.finalizeApprovalFromApprovalRequest(id, selfApproved))
                .hasMessageContaining("APPROVAL_PRODUCTION_MISSING");
    }

    private ApprovalRequest approval(UUID requester, UUID productionActor, UUID hseActor) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(UUID.randomUUID());
        request.setTargetType(ApprovalTargetType.PLANNED_SHUTDOWN);
        request.setTargetId(id);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setRequesterId(requester);
        request.setStatus(ApprovalStatus.APPROVED);
        request.setPayloadJson("{\"scopeVersion\":3}");
        request.setSteps(List.of(step(request, "PRODUCTION_MANAGER", productionActor),
                step(request, "HSE_MANAGER", hseActor)));
        return request;
    }

    private ApprovalStep step(ApprovalRequest request, String role, UUID decidedBy) {
        ApprovalStep step = new ApprovalStep();
        step.setRequest(request);
        step.setStepNumber(role.startsWith("PRODUCTION") ? 1 : 2);
        step.setApproverRole(role);
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(decidedBy);
        return step;
    }
}
