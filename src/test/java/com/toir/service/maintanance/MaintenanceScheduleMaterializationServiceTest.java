package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.MaintenanceScheduleApprovalStaleException;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import com.toir.service.PprPlanService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleMaterializationServiceTest {

    private static final String HASH = "c".repeat(64);

    @Mock
    private PprPlanRepository planRepository;
    @Mock
    private MaintenanceScheduleCalculationItemRepository itemRepository;
    @Mock
    private PprTaskRepository taskRepository;
    @Mock
    private PprPlanService legacyPlanService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private MaintenanceScheduleContentHasher hasher;

    private MaintenanceScheduleMaterializationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleMaterializationService(
                planRepository,
                itemRepository,
                taskRepository,
                legacyPlanService,
                entityManager,
                hasher,
                new MaintenanceScheduleCalculationContentFactory());
    }

    @Test
    void matchingExactTupleCreatesApprovedTasksFromOnlyThatRevision() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        ApprovalRequest request = request(planId, 2L, HASH);
        MaintenanceScheduleCalculationItem item = item(plan, 2L);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of(item));
        when(taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(item.getId()))).thenReturn(List.of());
        when(taskRepository.saveAll(any())).thenAnswer(invocation ->
                (List<PprTask>) invocation.getArgument(0));

        MaintenanceScheduleMaterializationOutcome outcome =
                service.finalizeApproval(request, UUID.randomUUID());

        assertThat(outcome).isEqualTo(MaintenanceScheduleMaterializationOutcome.APPROVED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.APPROVED);
        assertThat(plan.getTaskMaterializationStatus())
                .isEqualTo(TaskMaterializationStatus.MATERIALIZED);
        assertThat(plan.getMaterializedRevision()).isEqualTo(2L);
        assertThat(plan.getMaterializedTaskCount()).isEqualTo(1);
        verify(taskRepository).saveAll(org.mockito.ArgumentMatchers.argThat(tasks ->
                ((List<PprTask>) tasks).stream().allMatch(task ->
                        task.getStatus() == PprTaskStatus.APPROVED
                                && item.getId().equals(task.getSourceCalculationItemId()))));
    }

    @Test
    void staleRevisionCreatesNoTasksAndDoesNotApprovePlan() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 3L, "d".repeat(64));
        ApprovalRequest request = request(planId, 2L, HASH);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.finalizeApproval(request, UUID.randomUUID()))
                .isInstanceOf(MaintenanceScheduleApprovalStaleException.class);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CALCULATED);
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void exactRetryIsIdempotentOnlyWhenEverySnapshotItemIsAlreadyLinked() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        plan.setStatus(PlanStatus.APPROVED);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setMaterializedRevision(2L);
        plan.setMaterializedTaskCount(1);
        ApprovalRequest request = request(planId, 2L, HASH);
        MaintenanceScheduleCalculationItem item = item(plan, 2L);
        PprTask existing = new PprTask();
        existing.setSourceCalculationItemId(item.getId());
        existing.setPlan(plan);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of(item));
        when(taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(item.getId()))).thenReturn(List.of(existing));

        assertThat(service.finalizeApproval(request, UUID.randomUUID()))
                .isEqualTo(MaintenanceScheduleMaterializationOutcome.IDEMPOTENT_SUCCESS);
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void exactRetryRemainsIdempotentAfterPlanHasStarted() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        plan.setStatus(PlanStatus.IN_PROGRESS);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setMaterializedRevision(2L);
        plan.setMaterializedTaskCount(1);
        ApprovalRequest request = request(planId, 2L, HASH);
        MaintenanceScheduleCalculationItem item = item(plan, 2L);
        PprTask existing = new PprTask();
        existing.setSourceCalculationItemId(item.getId());
        existing.setPlan(plan);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of(item));
        when(taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(item.getId()))).thenReturn(List.of(existing));

        assertThat(service.finalizeApproval(request, UUID.randomUUID()))
                .isEqualTo(MaintenanceScheduleMaterializationOutcome.IDEMPOTENT_SUCCESS);
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void emptySnapshotDoesNotQueryExistingTasks() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizeApproval(
                request(planId, 2L, HASH), UUID.randomUUID()))
                .isInstanceOfSatisfying(RestException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo("PPR_CALCULATION_SNAPSHOT_MISSING"));

        verify(taskRepository, never())
                .findAllByPlanIdAndSourceCalculationItemIdIn(any(), any());
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void loadsMultipleSourceIdsInOnePlanBoundBatch() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        MaintenanceScheduleCalculationItem first = item(plan, 2L);
        MaintenanceScheduleCalculationItem second = item(plan, 2L);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of(first, second));
        when(taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(first.getId(), second.getId()))).thenReturn(List.of());
        when(taskRepository.saveAll(any())).thenAnswer(invocation ->
                (List<PprTask>) invocation.getArgument(0));

        assertThat(service.finalizeApproval(
                request(planId, 2L, HASH), UUID.randomUUID()))
                .isEqualTo(MaintenanceScheduleMaterializationOutcome.APPROVED);

        verify(taskRepository).findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(first.getId(), second.getId()));
        verify(taskRepository).saveAll(org.mockito.ArgumentMatchers.argThat(tasks ->
                ((List<PprTask>) tasks).stream()
                        .map(PprTask::getSourceCalculationItemId)
                        .toList()
                        .equals(List.of(first.getId(), second.getId()))));
    }

    @Test
    void partialMaterializationCreatesOnlyMissingTasksAndFinalizesPlan() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        MaintenanceScheduleCalculationItem first = item(plan, 2L);
        MaintenanceScheduleCalculationItem second = item(plan, 2L);
        PprTask existing = new PprTask();
        existing.setPlan(plan);
        existing.setSourceCalculationItemId(first.getId());
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of(first, second));
        when(taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(first.getId(), second.getId())))
                .thenReturn(List.of(existing));

        when(taskRepository.saveAll(any())).thenAnswer(invocation ->
                (List<PprTask>) invocation.getArgument(0));

        assertThat(service.finalizeApproval(
                request(planId, 2L, HASH), UUID.randomUUID()))
                .isEqualTo(MaintenanceScheduleMaterializationOutcome.APPROVED);

        verify(taskRepository).saveAll(org.mockito.ArgumentMatchers.argThat(tasks ->
                ((List<PprTask>) tasks).stream()
                        .map(PprTask::getSourceCalculationItemId)
                        .toList()
                        .equals(List.of(second.getId()))));
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.APPROVED);
        assertThat(plan.getTaskMaterializationStatus())
                .isEqualTo(TaskMaterializationStatus.MATERIALIZED);
        assertThat(plan.getMaterializedRevision()).isEqualTo(2L);
        assertThat(plan.getMaterializedTaskCount()).isEqualTo(2);
    }

    @Test
    void softDeletedSourceTaskFailsInsteadOfCreatingDuplicate() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, 2L, HASH);
        plan.setStatus(PlanStatus.APPROVED);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setMaterializedRevision(2L);
        plan.setMaterializedTaskCount(1);
        MaintenanceScheduleCalculationItem item = item(plan, 2L);
        PprTask deleted = new PprTask();
        deleted.setPlan(plan);
        deleted.setSourceCalculationItemId(item.getId());
        deleted.setDeleted(true);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan));
        when(itemRepository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                planId, 2L)).thenReturn(List.of(item));
        when(taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                planId, List.of(item.getId()))).thenReturn(List.of(deleted));

        assertInconsistentMaterialization(planId);
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void legacyPlanDelegatesWithoutRequiringRevisionTuple() {
        UUID planId = UUID.randomUUID();
        PprPlan legacy = new PprPlan();
        legacy.setId(planId);
        legacy.setMaterializationMode(MaterializationMode.LEGACY_MATERIALIZED);
        ApprovalRequest request = request(planId, null, null);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(legacy));

        assertThat(service.finalizeApproval(request, UUID.randomUUID()))
                .isEqualTo(MaintenanceScheduleMaterializationOutcome.LEGACY_APPROVED);
        verify(legacyPlanService).finalizeApprovalFromApprovalRequest(
                org.mockito.ArgumentMatchers.eq(planId), any());
    }

    private static PprPlan plan(UUID id, long revision, String hash) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("Annual");
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.setCalculationRevision(revision);
        plan.setCalculationContentHash(hash);
        plan.setCalculationContentHashVersion(1);
        return plan;
    }

    private static ApprovalRequest request(
            UUID planId, Long revision, String hash) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.PPR_PLAN);
        request.setTargetId(planId);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setCalculationRevision(revision);
        request.setCalculationContentHash(hash);
        request.setCalculationContentHashVersion(revision == null ? null : 1);
        return request;
    }

    private void assertInconsistentMaterialization(UUID planId) {
        assertThatThrownBy(() -> service.finalizeApproval(
                request(planId, 2L, HASH), UUID.randomUUID()))
                .isInstanceOfSatisfying(RestException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(
                                "PPR_CALCULATION_MATERIALIZATION_INCONSISTENT"));
    }

    private static MaintenanceScheduleCalculationItem item(
            PprPlan plan, long revision) {
        String sourceItemKey = UUID.randomUUID().toString().replace("-", "").repeat(2);
        MaintenanceScheduleCalculationItem item = MaintenanceScheduleCalculationItem.builder()
                .plan(plan)
                .calculationRevision(revision)
                .sourceItemKey(sourceItemKey)
                .sourceItemKeyVersion(1)
                .plannedDate(LocalDate.of(2026, 2, 1))
                .scheduledStart(LocalDateTime.of(2026, 2, 1, 9, 0))
                .scheduledEnd(LocalDateTime.of(2026, 2, 1, 18, 0))
                .dueDate(LocalDateTime.of(2026, 2, 1, 18, 0))
                .normativeLaborHours(new BigDecimal("8.0"))
                .priority(PriorityLevel.MEDIUM)
                .cycleOrdinal(1L)
                .taskTitleSnapshot("Maintenance")
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }
}
