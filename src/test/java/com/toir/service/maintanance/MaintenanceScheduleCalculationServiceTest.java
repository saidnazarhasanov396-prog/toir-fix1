package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewDiagnostic;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewItem;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewSummary;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleAnchorSource;
import com.toir.enums.MaintenanceScheduleContentHashVersion;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.config.AnnualMaintenanceApprovalFirstFeature;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.MaintenanceScheduleCalculationRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import com.toir.service.PprPlanService;
import com.toir.security.ScopeAccessService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleCalculationServiceTest {

    @Mock MaintenanceScheduleService scheduleService;
    @Mock PprPlanService pprPlanService;
    @Mock PprPlanRepository planRepository;
    @Mock MaintenanceScheduleCalculationRepository calculationRepository;
    @Mock MaintenanceScheduleCalculationItemRepository calculationItemRepository;
    @Mock ApprovalRequestRepository approvalRequestRepository;
    @Mock AnnualMaintenanceApprovalFirstFeature approvalFirstFeature;
    @Mock MaintenanceScheduleSnapshotDraftFactory snapshotDraftFactory;
    @Mock MaintenanceScheduleSnapshotService snapshotService;
    @Mock MaintenanceScheduleCalculationContentFactory contentFactory;
    @Mock MaintenanceScheduleContentHasher contentHasher;
    @Mock MaintenanceScheduleRevisionService revisionService;
    @Mock MaintenanceScheduleApprovalBindingService approvalBindingService;
    @Mock ScopeAccessService scopeAccessService;
    @Mock MaintenanceScheduleCalculationContent content;

    MaintenanceScheduleCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleCalculationService(
                scheduleService,
                pprPlanService,
                planRepository,
                calculationRepository,
                calculationItemRepository,
                approvalRequestRepository,
                approvalFirstFeature,
                snapshotDraftFactory,
                snapshotService,
                contentFactory,
                contentHasher,
                revisionService,
                approvalBindingService,
                scopeAccessService
        );
    }

    @Test
    void findByIdReturnsSavedItemsFromTheCurrentCalculationRevision() {
        MaintenanceScheduleCalculationRequest request = request();
        UUID planId = UUID.randomUUID();
        PprPlan entity = approvalFirstPlan(planId, request, "a".repeat(64));
        MaintenanceScheduleCalculationItem item = snapshotItem(entity, 1L);
        item.setId(UUID.randomUUID());
        item.setSourceItemKey("source-item-1");
        item.setEquipmentId(UUID.randomUUID());
        item.setEquipmentCodeSnapshot("EQ-101");
        item.setEquipmentNameSnapshot("Pump 101");
        item.setRegulationId(UUID.randomUUID());
        item.setRegulationNameSnapshot("Monthly service");
        item.setNormativeLaborHours(java.math.BigDecimal.valueOf(8));
        when(planRepository.findByIdAndIsDeletedFalse(planId))
                .thenReturn(java.util.Optional.of(entity));
        when(pprPlanService.findById(planId))
                .thenReturn(planDto(planId, PlanStatus.CALCULATED));
        when(calculationItemRepository
                .findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(planId, 1L))
                .thenReturn(List.of(item));

        var result = service.findById(planId);

        assertThat(result.calculationItems()).hasSize(1);
        assertThat(result.calculationItems().getFirst().taskTitle())
                .isEqualTo("Monthly service");
        assertThat(result.calculationItems().getFirst().equipmentName())
                .isEqualTo("Pump 101");
    }

    @Test
    void createPersistsASeparatedBuilderPlanWithoutStartingApproval() {
        MaintenanceScheduleCalculationRequest request = request();
        PprPlanDto saved = new PprPlanDto(
                UUID.randomUUID(),
                "PPR-2027-0001",
                "Вариант 2027",
                PlanStatus.GENERATED,
                request.departmentId(),
                null,
                request.createdById(),
                null,
                request.notes(),
                4,
                request.fromDate(),
                request.toDate()
        );
        when(scheduleService.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(previewItem()),
                new MaintenanceSchedulePreviewSummary(1, 4, 0, 0)
        ));
        when(pprPlanService.createScheduleCalculation(any())).thenReturn(saved);

        var result = service.create(request);

        assertThat(result.plan()).isSameAs(saved);
        assertThat(result.approvalStatus()).isNull();
        verify(pprPlanService).createScheduleCalculation(any());
        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsAnEmptyCalculationBeforePersistingAnything() {
        when(scheduleService.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(),
                new MaintenanceSchedulePreviewSummary(0, 0, 0, 2)
        ));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("no occurrences");

        verify(pprPlanService, never()).createScheduleCalculation(any());
    }

    @Test
    void createRejectsBlockingWeekdayShiftDiagnostics() {
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        when(scheduleService.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(),
                new MaintenanceSchedulePreviewSummary(1, 1, 0, 0),
                List.of(new MaintenanceSchedulePreviewDiagnostic(
                        "SHIFTED_OUTSIDE_PERIOD",
                        "BLOCKING",
                        equipmentId,
                        "EQ-1",
                        regulationId,
                        null,
                        "Monthly maintenance",
                        LocalDate.of(2027, 12, 31),
                        LocalDate.of(2028, 1, 1),
                        null
                ))
        ));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("blocking diagnostics");

        verify(pprPlanService, never()).createScheduleCalculation(any());
    }

    @Test
    void approvalFirstCreatePersistsRevisionOneAndZeroTasksAtomically() {
        MaintenanceScheduleCalculationRequest request = request();
        UUID planId = UUID.randomUUID();
        PprPlanDto created = planDto(planId, PlanStatus.DRAFT);
        PprPlanDto persisted = planDto(planId, PlanStatus.CALCULATED);
        PprPlan entity = new PprPlan();
        entity.setId(planId);
        entity.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        entity.setStatus(PlanStatus.DRAFT);
        entity.setStartDate(request.fromDate());
        entity.setEndDate(request.toDate());
        entity.setTasks(new java.util.ArrayList<>());
        entity.setTargets(new java.util.ArrayList<>());
        MaintenanceScheduleCalculationItem item =
                MaintenanceScheduleCalculationItem.builder()
                        .plan(entity)
                        .calculationRevision(1L)
                        .cycleOrdinal(1L)
                        .plannedDate(LocalDate.of(2027, 2, 1))
                        .taskTitleSnapshot("Monthly service")
                        .build();
        String hash = "a".repeat(64);
        when(approvalFirstFeature.isEnabled()).thenReturn(true);
        when(scheduleService.preview(any())).thenReturn(
                new MaintenanceSchedulePreviewResponse(
                        List.of(previewItem()),
                        new MaintenanceSchedulePreviewSummary(1, 1, 0, 0)));
        when(pprPlanService.createScheduleCalculation(any())).thenReturn(created);
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(java.util.Optional.of(entity));
        when(snapshotDraftFactory.create(entity, 1L, List.of(previewItem())))
                .thenReturn(List.of(item));
        when(contentFactory.fromSnapshot(entity, 1L, List.of(item)))
                .thenReturn(content);
        when(revisionService.initial(content)).thenReturn(
                new MaintenanceScheduleRevisionTransition(
                        null,
                        1L,
                        MaintenanceScheduleContentHashVersion.V1,
                        hash));
        when(pprPlanService.findById(planId)).thenReturn(persisted);

        var result = service.create(request);

        assertThat(result.plan().status()).isEqualTo(PlanStatus.CALCULATED);
        assertThat(entity.getCalculationRevision()).isEqualTo(1L);
        assertThat(entity.getCalculationContentHash()).isEqualTo(hash);
        assertThat(entity.getTasks()).isEmpty();
        verify(snapshotService).insertRevision(planId, 1L, List.of(item));
        verify(planRepository).saveAndFlush(entity);
    }

    @Test
    void sameCanonicalContentAmendIsAnExplicitRevisionNoOp() {
        MaintenanceScheduleCalculationRequest request = request();
        UUID planId = UUID.randomUUID();
        String hash = "a".repeat(64);
        PprPlan entity = approvalFirstPlan(planId, request, hash);
        MaintenanceScheduleCalculationItem item = snapshotItem(entity, 1L);
        when(planRepository.findByIdAndIsDeletedFalse(planId))
                .thenReturn(java.util.Optional.of(entity));
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(java.util.Optional.of(entity));
        when(scheduleService.preview(any())).thenReturn(
                new MaintenanceSchedulePreviewResponse(
                        List.of(previewItem()),
                        new MaintenanceSchedulePreviewSummary(1, 1, 0, 0)));
        when(pprPlanService.updateScheduleCalculation(any(), any()))
                .thenReturn(planDto(planId, PlanStatus.CALCULATED));
        when(snapshotDraftFactory.create(any(), anyLong(), any()))
                .thenReturn(List.of(item));
        when(contentFactory.fromSnapshot(entity, 1L, List.of(item)))
                .thenReturn(content);
        when(contentHasher.compute(1, content)).thenReturn(hash);
        when(pprPlanService.findById(planId))
                .thenReturn(planDto(planId, PlanStatus.CALCULATED));

        var result = service.update(planId, request);

        assertThat(result.plan().generationMessage()).startsWith("NO_CHANGES:");
        verify(snapshotService, never()).insertRevision(any(), anyLong(), any());
        verify(approvalBindingService, never()).supersedeForNewRevision(
                any(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void changedAmendAppendsRevisionAndSupersedesOldPendingApproval() {
        MaintenanceScheduleCalculationRequest request = request();
        UUID planId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PprPlan entity = approvalFirstPlan(planId, request, "a".repeat(64));
        MaintenanceScheduleCalculationItem item = snapshotItem(entity, 1L);
        when(planRepository.findByIdAndIsDeletedFalse(planId))
                .thenReturn(java.util.Optional.of(entity));
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(java.util.Optional.of(entity));
        when(scheduleService.preview(any())).thenReturn(
                new MaintenanceSchedulePreviewResponse(
                        List.of(previewItem()),
                        new MaintenanceSchedulePreviewSummary(1, 1, 0, 0)));
        when(pprPlanService.updateScheduleCalculation(any(), any()))
                .thenReturn(planDto(planId, PlanStatus.CALCULATED));
        when(snapshotDraftFactory.create(any(), anyLong(), any()))
                .thenReturn(List.of(item));
        when(contentFactory.fromSnapshot(entity, 1L, List.of(item)))
                .thenReturn(content);
        when(contentFactory.fromSnapshot(entity, 2L, List.of(item)))
                .thenReturn(content);
        when(contentHasher.compute(1, content)).thenReturn("b".repeat(64));
        when(revisionService.next(1L, content)).thenReturn(
                new MaintenanceScheduleRevisionTransition(
                        1L,
                        2L,
                        MaintenanceScheduleContentHashVersion.V1,
                        "b".repeat(64)));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(pprPlanService.findById(planId))
                .thenReturn(planDto(planId, PlanStatus.CALCULATED));

        var result = service.update(planId, request);

        assertThat(entity.getCalculationRevision()).isEqualTo(2L);
        assertThat(item.getCalculationRevision()).isEqualTo(2L);
        assertThat(result.plan().generationMessage()).isEqualTo("REVISION_CREATED:2");
        verify(snapshotService).insertRevision(planId, 2L, List.of(item));
        verify(approvalBindingService).supersedeForNewRevision(
                planId, 2L, "b".repeat(64), 1, actorId);
    }

    @Test
    void requestCreatesAPprPayloadMarkedByTheDedicatedServiceOrigin() {
        assertThat(request().toPprPlanRequest().anchorMode())
                .isEqualTo(MaintenanceScheduleAnchorMode.CURRENT);
        assertThat(PprPlanOrigin.MAINTENANCE_SCHEDULE.name())
                .isEqualTo("MAINTENANCE_SCHEDULE");
    }

    private MaintenanceScheduleCalculationRequest request() {
        return new MaintenanceScheduleCalculationRequest(
                "Вариант 2027",
                "Проверочный вариант",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(UUID.randomUUID()),
                List.of(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                MaintenanceScheduleAnchorMode.CURRENT
        );
    }

    private MaintenanceSchedulePreviewItem previewItem() {
        return new MaintenanceSchedulePreviewItem(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "EQ-101",
                "Pump 101",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                null,
                "Monthly service",
                MaintenanceKind.PREVENTIVE,
                PeriodicityUnit.MONTH,
                1,
                LocalDate.of(2027, 2, 1),
                MaintenanceScheduleAnchorSource.PLAN_START,
                8.0d,
                false);
    }

    private PprPlanDto planDto(UUID id, PlanStatus status) {
        MaintenanceScheduleCalculationRequest request = request();
        return new PprPlanDto(
                id,
                "PPR-2027-0001",
                request.name(),
                status,
                request.departmentId(),
                null,
                request.createdById(),
                null,
                request.notes(),
                0,
                request.fromDate(),
                request.toDate());
    }

    private PprPlan approvalFirstPlan(
            UUID id,
            MaintenanceScheduleCalculationRequest request,
            String hash) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(
                TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.setStartDate(request.fromDate());
        plan.setEndDate(request.toDate());
        plan.setCalculationRevision(1L);
        plan.setCalculationContentHash(hash);
        plan.setCalculationContentHashVersion(1);
        plan.setTasks(new java.util.ArrayList<>());
        plan.setTargets(new java.util.ArrayList<>());
        return plan;
    }

    private MaintenanceScheduleCalculationItem snapshotItem(
            PprPlan plan, long revision) {
        return MaintenanceScheduleCalculationItem.builder()
                .plan(plan)
                .calculationRevision(revision)
                .cycleOrdinal(1L)
                .plannedDate(LocalDate.of(2027, 2, 1))
                .taskTitleSnapshot("Monthly service")
                .build();
    }
}
