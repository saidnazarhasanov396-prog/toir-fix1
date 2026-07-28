package com.toir.service;

import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.dto.inspection.InspectionDashboardSummaryDto;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.InspectionRoundStatus;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock
    InspectionRouteRepository routeRepo;

    @Mock
    InspectionCheckpointRepository checkpointRepo;

    @Mock
    InspectionRoundRepository roundRepo;

    @Mock
    DefectRepository defectRepo;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    InspectionService service;

    @BeforeEach
    void setUpScopeAdminBypass() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(checkpointRepo.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID checkpointId = invocation.getArgument(0);
            return Optional.of(checkpoint(checkpointId, null, null, "Checkpoint"));
        });
        lenient().when(notificationService.notifyDepartmentByPermission(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());
    }

    @Test
    void listRoundsWithNullFiltersReturnsEmptyList() {
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of());

        assertThat(service.listRounds(null, null, null)).isEmpty();
        verify(roundRepo).findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null);
    }

    @Test
    void listRoundsWithRouteAndStatusFiltersReturnsData() {
        UUID routeId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), InspectionRoundStatus.IN_PROGRESS);
        InspectionRoute route = new InspectionRoute();
        ReflectionTestUtils.setField(route, "id", routeId);
        round.setRoute(route);
        round.setPerformedBy(performedBy);
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route));
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(
                eq(routeId),
                eq(performedBy),
                eq(InspectionRoundStatus.IN_PROGRESS)
        )).thenReturn(List.of(round));

        assertThat(service.listRounds(routeId, performedBy, InspectionRoundStatus.IN_PROGRESS))
                .hasSize(1)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo(InspectionRoundStatus.IN_PROGRESS);
                    assertThat(item.results()).isEmpty();
                });
    }

    @Test
    void dashboardSummaryMarksRoutesWithoutCompletedRoundsFromCreatedAt() {
        Instant now = Instant.parse("2026-06-22T06:00:00Z");
        InspectionRoute route = routeWithSchedule(
                UUID.randomUUID(),
                "IR-DUE",
                "Daily pump route",
                "DAILY",
                Instant.parse("2026-06-21T04:00:00Z"),
                2
        );
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(null, true, null))
                .thenReturn(List.of(route));
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of());

        InspectionDashboardSummaryDto summary = service.getDashboardSummary(null, now);

        assertThat(summary.activeRoutes()).isEqualTo(1);
        assertThat(summary.dueToday()).isEqualTo(0);
        assertThat(summary.overdue()).isEqualTo(1);
        assertThat(summary.inProgress()).isZero();
        assertThat(summary.attentionRoutes()).singleElement().satisfies(item -> {
            assertThat(item.routeId()).isEqualTo(route.getId());
            assertThat(item.routeCode()).isEqualTo("IR-DUE");
            assertThat(item.checkpointsCount()).isEqualTo(2);
            assertThat(item.nextDueAt()).isEqualTo(Instant.parse("2026-06-22T04:00:00Z"));
            assertThat(item.state()).isEqualTo("OVERDUE");
        });
    }

    @Test
    void dashboardSummaryKeepsDailyRouteCompletedTodayOutOfAttention() {
        Instant now = Instant.parse("2026-06-22T06:00:00Z");
        UUID routeId = UUID.randomUUID();
        InspectionRoute route = routeWithSchedule(
                routeId,
                "IR-OK",
                "Daily completed route",
                "DAILY",
                Instant.parse("2026-06-19T05:00:00Z"),
                1
        );
        InspectionRound completed = roundForRoute(
                UUID.randomUUID(),
                route,
                InspectionRoundStatus.COMPLETED,
                Instant.parse("2026-06-22T02:00:00Z"),
                Instant.parse("2026-06-22T02:30:00Z"),
                1,
                0
        );
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(null, true, null))
                .thenReturn(List.of(route));
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of(completed));

        InspectionDashboardSummaryDto summary = service.getDashboardSummary(null, now);

        assertThat(summary.activeRoutes()).isEqualTo(1);
        assertThat(summary.completedToday()).isEqualTo(1);
        assertThat(summary.findingsToday()).isEqualTo(1);
        assertThat(summary.alarmsToday()).isZero();
        assertThat(summary.overdue()).isZero();
        assertThat(summary.dueToday()).isZero();
        assertThat(summary.attentionRoutes()).isEmpty();
    }

    @Test
    void dashboardSummaryCalculatesShiftWeeklyAndMonthlyNextDueTimes() {
        Instant now = Instant.parse("2026-06-22T06:00:00Z");
        InspectionRoute shiftRoute = routeWithSchedule(
                UUID.randomUUID(),
                "IR-SHIFT",
                "Shift route",
                "SHIFT",
                Instant.parse("2026-06-20T00:00:00Z"),
                1
        );
        InspectionRoute weeklyRoute = routeWithSchedule(
                UUID.randomUUID(),
                "IR-WEEK",
                "Weekly route",
                "WEEKLY",
                Instant.parse("2026-06-20T00:00:00Z"),
                1
        );
        InspectionRoute monthlyRoute = routeWithSchedule(
                UUID.randomUUID(),
                "IR-MONTH",
                "Monthly route",
                "MONTHLY",
                Instant.parse("2026-06-20T00:00:00Z"),
                1
        );
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(null, true, null))
                .thenReturn(List.of(shiftRoute, weeklyRoute, monthlyRoute));
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of(
                        roundForRoute(UUID.randomUUID(), shiftRoute, InspectionRoundStatus.COMPLETED,
                                Instant.parse("2026-06-21T20:00:00Z"), Instant.parse("2026-06-21T21:00:00Z"), 0, 0),
                        roundForRoute(UUID.randomUUID(), weeklyRoute, InspectionRoundStatus.COMPLETED,
                                Instant.parse("2026-06-15T05:00:00Z"), Instant.parse("2026-06-15T05:30:00Z"), 0, 0),
                        roundForRoute(UUID.randomUUID(), monthlyRoute, InspectionRoundStatus.COMPLETED,
                                Instant.parse("2026-05-22T04:00:00Z"), Instant.parse("2026-05-22T04:30:00Z"), 0, 0)
                ));

        InspectionDashboardSummaryDto summary = service.getDashboardSummary(null, now);

        assertThat(summary.attentionRoutes())
                .extracting("routeCode", "nextDueAt", "state")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("IR-MONTH", Instant.parse("2026-06-21T04:30:00Z"), "OVERDUE"),
                        org.assertj.core.groups.Tuple.tuple("IR-SHIFT", Instant.parse("2026-06-22T05:00:00Z"), "OVERDUE"),
                        org.assertj.core.groups.Tuple.tuple("IR-WEEK", Instant.parse("2026-06-22T05:30:00Z"), "OVERDUE")
                );
    }

    @Test
    void dashboardSummaryCountsInProgressRoutesSeparatelyFromOverdue() {
        Instant now = Instant.parse("2026-06-22T06:00:00Z");
        InspectionRoute route = routeWithSchedule(
                UUID.randomUUID(),
                "IR-ACTIVE",
                "Active overdue route",
                "SHIFT",
                Instant.parse("2026-06-21T00:00:00Z"),
                3
        );
        InspectionRound activeRound = roundForRoute(
                UUID.randomUUID(),
                route,
                InspectionRoundStatus.IN_PROGRESS,
                Instant.parse("2026-06-22T05:00:00Z"),
                null,
                2,
                1
        );
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(null, true, null))
                .thenReturn(List.of(route));
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of(activeRound));

        InspectionDashboardSummaryDto summary = service.getDashboardSummary(null, now);

        assertThat(summary.inProgress()).isEqualTo(1);
        assertThat(summary.overdue()).isZero();
        assertThat(summary.findingsToday()).isEqualTo(2);
        assertThat(summary.alarmsToday()).isEqualTo(1);
        assertThat(summary.attentionRoutes()).singleElement().satisfies(item -> {
            assertThat(item.state()).isEqualTo("IN_PROGRESS");
            assertThat(item.activeRoundId()).isEqualTo(activeRound.getId());
            assertThat(item.activeFindingsCount()).isEqualTo(2);
            assertThat(item.activeAlarmCount()).isEqualTo(1);
        });
    }

    @Test
    void inProgressRoundCanRecordResult() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("bar")).thenReturn("bar");
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> {
            InspectionRound saved = invocation.getArgument(0);
            for (InspectionRoundResult item : saved.getResults()) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
                }
            }
            return saved;
        });

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", 10.0, "bar", "ok", null)
        );

        assertThat(result.roundId()).isEqualTo(roundId);
        assertThat(result.status()).isEqualTo("OK");
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void inProgressRoundCanComplete() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundDto result = service.completeRound(roundId, "done");

        assertThat(result.status()).isEqualTo(InspectionRoundStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void inProgressRoundCanRecordResultWithoutMeasuredUnit() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", 11.0, null, "ok", null)
        );

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.measuredUnit()).isNull();
    }

    @Test
    void failResultCreatesInspectionDefectAndRepairRequestWhenRequiredDataExists() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, departmentId, performerId);
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, equipmentId, locationId, "Pump vibration");
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(defectRepo.findByCodeAndIsDeletedFalse("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId)))
                .thenReturn(Optional.empty());
        when(repairRequestRepository.existsByNumberAndIsDeletedFalse("INS-RR-" + shortId(roundId) + "-" + shortId(checkpointId)))
                .thenReturn(false);
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(defectRepo.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "bearing noise", null)
        );

        assertThat(result.defectId()).isNotNull();
        assertThat(round.getFindingsCount()).isEqualTo(1);
        assertThat(round.getAlarmCount()).isEqualTo(1);

        ArgumentCaptor<RepairRequest> repairCaptor = ArgumentCaptor.forClass(RepairRequest.class);
        verify(repairRequestRepository).save(repairCaptor.capture());
        RepairRequest repairRequest = repairCaptor.getValue();
        assertThat(repairRequest.getNumber()).isEqualTo("INS-RR-" + shortId(roundId) + "-" + shortId(checkpointId));
        assertThat(repairRequest.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(repairRequest.getDepartmentId()).isEqualTo(departmentId);
        assertThat(repairRequest.getLocationId()).isEqualTo(locationId);
        assertThat(repairRequest.getReporterId()).isEqualTo(performerId);
        assertThat(repairRequest.getSource()).isEqualTo(RequestSource.INSPECTION);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.OPEN);

        ArgumentCaptor<Defect> defectCaptor = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepo).save(defectCaptor.capture());
        Defect defect = defectCaptor.getValue();
        assertThat(defect.getCode()).isEqualTo("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId));
        assertThat(defect.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(defect.getCategory()).isEqualTo("INSPECTION");
        assertThat(defect.getSeverity()).isEqualTo("CRITICAL");
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.OPEN);
        assertThat(defect.getRepairRequestId()).isEqualTo(repairRequest.getId());
        assertThat(defect.getDescription()).contains(roundId.toString(), checkpointId.toString(), "bearing noise");
        verify(notificationService).notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.DEFECT_READ),
                org.mockito.ArgumentMatchers.contains("Inspection FAIL triage"),
                org.mockito.ArgumentMatchers.contains(defect.getCode()),
                eq(com.toir.enums.NotificationSeverity.WARNING),
                eq(com.toir.enums.NotificationEventType.DEFECT_CREATED_FROM_INSPECTION),
                eq("DEFECT"),
                eq(defect.getId().toString())
        );
    }

    @Test
    void failResultCreatesInspectionDefectOnlyWhenRepairRequestRequiredDataIsMissing() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, null, UUID.randomUUID());
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, equipmentId, null, "Pump vibration");
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(defectRepo.findByCodeAndIsDeletedFalse("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId)))
                .thenReturn(Optional.empty());
        when(defectRepo.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "bearing noise", null)
        );

        assertThat(result.defectId()).isNotNull();
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
        ArgumentCaptor<Defect> defectCaptor = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepo).save(defectCaptor.capture());
        assertThat(defectCaptor.getValue().getRepairRequestId()).isNull();
    }

    @Test
    void repeatedFailForSameRoundAndCheckpointReusesOpenInspectionDefectAndRepairRequest() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID existingDefectId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, UUID.randomUUID(), UUID.randomUUID());
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, equipmentId, null, "Pump vibration");
        Defect existing = new Defect();
        ReflectionTestUtils.setField(existing, "id", existingDefectId);
        existing.setCode("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId));
        existing.setEquipmentId(equipmentId);
        existing.setCategory("INSPECTION");
        existing.setSeverity("CRITICAL");
        existing.setStatus(DefectStatus.OPEN);
        existing.setRepairRequestId(repairRequestId);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(defectRepo.findByCodeAndIsDeletedFalse(existing.getCode())).thenReturn(Optional.of(existing));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "repeat fail", null)
        );

        assertThat(result.defectId()).isEqualTo(existingDefectId);
        verify(defectRepo, never()).save(any(Defect.class));
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void warnAndOkResultsDoNotCreateDefectOrRepairRequest() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "WARN", null, null, "watch", null)
        );
        service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, "ok", null)
        );

        assertThat(round.getFindingsCount()).isEqualTo(1);
        assertThat(round.getAlarmCount()).isZero();
        verify(defectRepo, never()).save(any(Defect.class));
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
        verify(roundRepo, times(2)).save(any(InspectionRound.class));
    }

    @Test
    void completedRoundCannotRecordResultOrCompleteAgain() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.COMPLETED);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot add results");

        assertThatThrownBy(() -> service.completeRound(roundId, "again"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Round is not IN_PROGRESS");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void missingCheckpointRemainsNotFoundWhenRecordingResult() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Inspection checkpoint not found");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void failWithoutEquipmentIsRejectedBecauseItCannotBeTriaged() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, UUID.randomUUID(), UUID.randomUUID());
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, null, null, "Area housekeeping");
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "unsafe", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("linked to equipment");

        verify(defectRepo, never()).save(any(Defect.class));
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void cancelledRoundCannotRecordResultOrCompleteAgain() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.CANCELLED);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot add results");

        assertThatThrownBy(() -> service.completeRound(roundId, "again"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Round is not IN_PROGRESS");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void createRouteWithKnownCheckpointUnitNormalizesAndSucceeds() {
        stubNextRouteCode();
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull(" bar ")).thenReturn("bar");
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });

        InspectionRouteRequest request = routeRequest(" bar ");

        InspectionRouteDto dto = service.createRoute(request);

        assertThat(dto.checkpoints()).hasSize(1);
        assertThat(dto.checkpoints().getFirst().expectedUnit()).isEqualTo("bar");
        verify(routeRepo).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteWithUnknownCheckpointUnitReturnsBadRequest() {
        stubNextRouteCode();
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("mystery-unit"))
                .thenThrow(RestException.badRequest("Unknown unit"));

        assertThatThrownBy(() -> service.createRoute(routeRequest("mystery-unit")))
                .isInstanceOf(RestException.class)
                .extracting(ex -> ((RestException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(routeRepo, never()).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteWithBlankCheckpointUnitStoresNullWhenOptional() {
        stubNextRouteCode();
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("   ")).thenReturn(null);
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });

        InspectionRouteDto dto = service.createRoute(routeRequest("   "));

        assertThat(dto.checkpoints()).hasSize(1);
        assertThat(dto.checkpoints().getFirst().expectedUnit()).isNull();
        verify(routeRepo).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteRejectsClientProvidedCode() {
        assertThatThrownBy(() -> service.createRoute(routeRequest("IR-CLIENT", "PCS")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("code is generated by backend");
                });

        verify(routeRepo, never()).save(any());
    }

    private InspectionRouteRequest routeRequest(String expectedUnit) {
        return routeRequest(null, expectedUnit);
    }

    private InspectionRoute routeWithSchedule(
            UUID id,
            String code,
            String name,
            String frequency,
            Instant createdAt,
            int checkpointCount
    ) {
        InspectionRoute route = new InspectionRoute();
        ReflectionTestUtils.setField(route, "id", id);
        ReflectionTestUtils.setField(route, "createdAt", createdAt);
        ReflectionTestUtils.setField(route, "updatedAt", createdAt);
        route.setCode(code);
        route.setName(name);
        route.setFrequency(frequency);
        route.setTargetDurationMin(30);
        route.setActive(true);
        for (int i = 1; i <= checkpointCount; i++) {
            InspectionCheckpoint checkpoint = new InspectionCheckpoint();
            ReflectionTestUtils.setField(checkpoint, "id", UUID.randomUUID());
            checkpoint.setRoute(route);
            checkpoint.setOrderIndex(i);
            checkpoint.setTitle("Checkpoint " + i);
            route.getCheckpoints().add(checkpoint);
        }
        return route;
    }

    private InspectionRound roundForRoute(
            UUID id,
            InspectionRoute route,
            InspectionRoundStatus status,
            Instant startedAt,
            Instant completedAt,
            int findingsCount,
            int alarmCount
    ) {
        InspectionRound round = new InspectionRound();
        ReflectionTestUtils.setField(round, "id", id);
        ReflectionTestUtils.setField(round, "createdAt", startedAt);
        ReflectionTestUtils.setField(round, "updatedAt", completedAt != null ? completedAt : startedAt);
        round.setRoute(route);
        round.setStartedAt(startedAt);
        round.setCompletedAt(completedAt);
        round.setStatus(status);
        round.setPerformedBy(UUID.randomUUID());
        round.setFindingsCount(findingsCount);
        round.setAlarmCount(alarmCount);
        return round;
    }

    private InspectionRouteRequest routeRequest(String code, String expectedUnit) {
        InspectionRouteRequest.CheckpointRequest checkpoint = new InspectionRouteRequest.CheckpointRequest(
                1,
                null,
                null,
                "Checkpoint",
                "Instruction",
                "MEASUREMENT",
                0.0,
                10.0,
                expectedUnit,
                true
        );
        return new InspectionRouteRequest(
                code,
                "Route 1",
                null,
                "DAILY",
                15,
                "desc",
                true,
                List.of(checkpoint)
        );
    }

    private void stubNextRouteCode() {
        String codePrefix = "IR-" + java.time.Year.now().getValue() + "-";
        when(routeRepo.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(routeRepo.existsByCodeAndIsDeletedFalse(codePrefix + "0001")).thenReturn(false);
    }

    private InspectionRound round(UUID id, InspectionRoundStatus status) {
        InspectionRound round = new InspectionRound();
        ReflectionTestUtils.setField(round, "id", id);
        round.setStatus(status);
        round.setPerformedBy(UUID.randomUUID());
        return round;
    }

    private InspectionRound roundWithRoute(UUID id, UUID departmentId, UUID performerId) {
        InspectionRoute route = new InspectionRoute();
        ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
        route.setCode("IR-001");
        route.setName("Route");
        route.setDepartmentId(departmentId);
        InspectionRound round = round(id, InspectionRoundStatus.IN_PROGRESS);
        round.setRoute(route);
        round.setPerformedBy(performerId);
        return round;
    }

    private InspectionCheckpoint checkpoint(UUID id, UUID equipmentId, UUID locationId, String title) {
        InspectionCheckpoint checkpoint = new InspectionCheckpoint();
        ReflectionTestUtils.setField(checkpoint, "id", id);
        checkpoint.setEquipmentId(equipmentId);
        checkpoint.setLocationId(locationId);
        checkpoint.setTitle(title);
        return checkpoint;
    }

    private Equipment equipment(UUID equipmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", equipmentId);
        equipment.setCode("EQ-001");
        equipment.setName("Pump");
        equipment.setDepartmentId(departmentId);
        return equipment;
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
