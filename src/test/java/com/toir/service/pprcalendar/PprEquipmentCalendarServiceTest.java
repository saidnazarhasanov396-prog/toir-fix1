package com.toir.service.pprcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarOccurrenceSourceType;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarResponse;
import com.toir.entity.PprPlan;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarDiagnostics;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarEquipment;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarOccurrence;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.EquipmentIdPage;
import com.toir.security.ScopeAccessService;
import com.toir.service.PprPlanVisibilityPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PprEquipmentCalendarServiceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_DEPARTMENT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Mock PprPlanRepository planRepository;
    @Mock PprPlanVisibilityPolicy visibilityPolicy;
    @Mock ScopeAccessService scopeAccessService;
    @Mock PprEquipmentCalendarQueryRepository queryRepository;

    private PprEquipmentCalendarService service;

    @BeforeEach
    void setUp() {
        service = new PprEquipmentCalendarService(
                planRepository,
                visibilityPolicy,
                scopeAccessService,
                new PprEquipmentCalendarAuthoritativeSourceResolver(),
                queryRepository,
                new MaintenanceKindDisplayResolver());
        lenient().when(planRepository.findByIdAndIsDeletedFalse(PLAN_ID))
                .thenReturn(Optional.of(plan()));
        lenient().when(visibilityPolicy.isVisible(any())).thenReturn(true);
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(queryRepository.findLatestApprovalStatus(PLAN_ID))
                .thenReturn(ApprovalStatus.APPROVED);
        lenient().when(queryRepository.findEquipmentPage(any())).thenReturn(new EquipmentIdPage(
                List.of(EQUIPMENT_ID), 1));
        lenient().when(queryRepository.findEquipmentMetadata(List.of(EQUIPMENT_ID)))
                .thenReturn(List.of(equipment(EQUIPMENT_ID, "Pump")));
        lenient().when(queryRepository.findDiagnostics(any()))
                .thenReturn(new CalendarDiagnostics(0, 0, 0, 0));
    }

    @Test
    void returnsTwelveBucketsAndKeepsTwoOccurrencesInTheirEffectiveStartMonth() {
        LocalDateTime januaryStart = LocalDateTime.of(2026, 1, 31, 20, 0);
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of(
                occurrence(
                        UUID.fromString("40000000-0000-0000-0000-000000000001"),
                        null,
                        LocalDate.of(2026, 1, 5),
                        januaryStart,
                        LocalDateTime.of(2026, 2, 1, 4, 0),
                        LocalDateTime.of(2026, 9, 1, 0, 0),
                        MaintenanceKind.INSPECTION),
                occurrence(
                        UUID.fromString("40000000-0000-0000-0000-000000000002"),
                        null,
                        LocalDate.of(2026, 1, 20),
                        LocalDateTime.of(2026, 1, 20, 8, 0),
                        LocalDateTime.of(2026, 1, 20, 10, 0),
                        LocalDateTime.of(2026, 11, 1, 0, 0),
                        MaintenanceKind.CURRENT_REPAIR)));

        PprEquipmentCalendarResponse response =
                service.getCalendar(PLAN_ID, PprEquipmentCalendarFilter.forYear(2026));

        assertThat(response.content()).singleElement().satisfies(row -> {
            assertThat(row.months()).containsOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
            assertThat(row.months().keySet())
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
            assertThat(row.months().get(1)).hasSize(2);
            assertThat(row.months().get(2)).isEmpty();
            assertThat(row.months().get(9)).isEmpty();
            assertThat(row.yearTaskCount()).isEqualTo(2);
        });
    }

    @Test
    void preservesDeletedEquipmentAndFallsBackForAnUnresolvedMaintenanceKind() {
        when(queryRepository.findEquipmentMetadata(List.of(EQUIPMENT_ID)))
                .thenReturn(List.of(equipment(EQUIPMENT_ID, "Historical pump", true)));
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of(occurrence(
                UUID.fromString("40000000-0000-0000-0000-000000000009"),
                null,
                LocalDate.of(2026, 5, 10),
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 10, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                null)));

        PprEquipmentCalendarResponse response =
                service.getCalendar(PLAN_ID, PprEquipmentCalendarFilter.forYear(2026));

        assertThat(response.content()).singleElement().satisfies(row -> {
            assertThat(row.equipment().deleted()).isTrue();
            assertThat(row.months().get(5)).singleElement().satisfies(occurrence -> {
                assertThat(occurrence.maintenanceKind()).isNull();
                assertThat(occurrence.displayCode()).isEqualTo("—");
                assertThat(occurrence.displayName()).isEqualTo("Вид обслуживания не определён");
            });
        });
    }

    @Test
    void linkedSnapshotPlannedDateWinsAndLegacyTaskFallsBackToScheduledStart() {
        CalendarOccurrence linked = occurrence(
                UUID.fromString("40000000-0000-0000-0000-000000000003"),
                UUID.fromString("50000000-0000-0000-0000-000000000003"),
                LocalDate.of(2026, 3, 10),
                LocalDateTime.of(2026, 8, 10, 8, 0),
                LocalDateTime.of(2026, 8, 10, 12, 0),
                LocalDateTime.of(2026, 12, 10, 0, 0),
                MaintenanceKind.PREVENTIVE);
        CalendarOccurrence legacy = occurrence(
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                null,
                null,
                LocalDateTime.of(2026, 4, 12, 8, 0),
                LocalDateTime.of(2026, 4, 12, 12, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0),
                MaintenanceKind.DIAGNOSTIC);
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of(linked, legacy));

        PprEquipmentCalendarResponse response =
                service.getCalendar(PLAN_ID, PprEquipmentCalendarFilter.forYear(2026));

        assertThat(response.content().getFirst().months().get(3))
                .extracting(occurrence -> occurrence.taskId())
                .containsExactly(linked.taskId());
        assertThat(response.content().getFirst().months().get(4))
                .extracting(occurrence -> occurrence.taskId())
                .containsExactly(legacy.taskId());
        assertThat(response.content().getFirst().months().get(8)).isEmpty();
        assertThat(response.content().getFirst().months().get(10)).isEmpty();
        assertThat(response.content().getFirst().months().get(12)).isEmpty();
    }

    @Test
    void rowPaginationLoadsCompleteOccurrencesAndMetadataOnceForOnlyTheCurrentPage() {
        UUID secondEquipmentId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                2026, 2, 2, null, null, null, null, Set.of(), Set.of(), true, false);
        when(queryRepository.findEquipmentPage(any())).thenReturn(new EquipmentIdPage(
                List.of(EQUIPMENT_ID, secondEquipmentId), 9));
        when(queryRepository.findEquipmentMetadata(List.of(EQUIPMENT_ID, secondEquipmentId)))
                .thenReturn(List.of(
                        equipment(EQUIPMENT_ID, "Pump"),
                        equipment(secondEquipmentId, "Valve")));
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of(
                occurrence(
                        UUID.fromString("40000000-0000-0000-0000-000000000005"),
                        null,
                        LocalDate.of(2026, 5, 2),
                        LocalDateTime.of(2026, 5, 2, 8, 0),
                        LocalDateTime.of(2026, 5, 2, 9, 0),
                        LocalDateTime.of(2026, 5, 3, 0, 0),
                        MaintenanceKind.INSPECTION),
                occurrenceFor(
                        secondEquipmentId,
                        UUID.fromString("40000000-0000-0000-0000-000000000006"),
                        LocalDate.of(2026, 7, 2))));

        PprEquipmentCalendarResponse response = service.getCalendar(PLAN_ID, filter);

        assertThat(response.page().number()).isEqualTo(2);
        assertThat(response.page().size()).isEqualTo(2);
        assertThat(response.page().totalElements()).isEqualTo(9);
        assertThat(response.page().totalPages()).isEqualTo(5);
        assertThat(response.content()).hasSize(2);
        verify(queryRepository).findEquipmentMetadata(List.of(EQUIPMENT_ID, secondEquipmentId));
        verify(queryRepository).findOccurrences(any(), org.mockito.ArgumentMatchers.eq(
                List.of(EQUIPMENT_ID, secondEquipmentId)));
    }

    @Test
    void returnsAllFourFullDatasetDiagnosticsWithoutPageRecalculation() {
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of());
        when(queryRepository.findDiagnostics(any())).thenReturn(new CalendarDiagnostics(3, 4, 5, 6));

        PprEquipmentCalendarResponse response =
                service.getCalendar(PLAN_ID, PprEquipmentCalendarFilter.forYear(2026));

        assertThat(response.excluded().missingEquipment()).isEqualTo(3);
        assertThat(response.excluded().unresolvedEquipment()).isEqualTo(4);
        assertThat(response.excluded().outsidePlanYear()).isEqualTo(5);
        assertThat(response.excluded().outsidePlanRange()).isEqualTo(6);
    }

    @Test
    void usesRuleOnlyCanonicalKindAndCentralDisplayMapping() {
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of(
                occurrence(
                        UUID.fromString("40000000-0000-0000-0000-000000000007"),
                        null,
                        LocalDate.of(2026, 6, 1),
                        LocalDateTime.of(2026, 6, 1, 8, 0),
                        LocalDateTime.of(2026, 6, 1, 10, 0),
                        LocalDateTime.of(2026, 6, 1, 12, 0),
                        MaintenanceKind.OVERHAUL)));

        PprEquipmentCalendarResponse response =
                service.getCalendar(PLAN_ID, PprEquipmentCalendarFilter.forYear(2026));

        assertThat(response.content().getFirst().months().get(6)).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo(
                    PprEquipmentCalendarOccurrenceSourceType.MAINTENANCE_RULE);
            assertThat(item.maintenanceKind()).isEqualTo(MaintenanceKind.OVERHAUL);
            assertThat(item.displayCode()).isEqualTo("КР");
            assertThat(item.displayName()).isEqualTo("Капитальный ремонт");
        });
    }

    @Test
    void selectsExactlyOneAuthoritativeSourceAndReportsRevisionOnlyForSnapshotMode() {
        PprPlan snapshotPlan = plan();
        snapshotPlan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        snapshotPlan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        snapshotPlan.setStatus(PlanStatus.CALCULATED);
        snapshotPlan.setCalculationRevision(7L);
        when(planRepository.findByIdAndIsDeletedFalse(PLAN_ID)).thenReturn(Optional.of(snapshotPlan));
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of());

        PprEquipmentCalendarResponse response =
                service.getCalendar(PLAN_ID, PprEquipmentCalendarFilter.forYear(2026));

        assertThat(response.authoritativeSource())
                .isEqualTo(PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT);
        assertThat(response.sourceRevision()).isEqualTo(7L);
        ArgumentCaptor<PprEquipmentCalendarQueryRepository.CalendarQuery> queryCaptor =
                ArgumentCaptor.forClass(PprEquipmentCalendarQueryRepository.CalendarQuery.class);
        verify(queryRepository).findEquipmentPage(queryCaptor.capture());
        assertThat(queryCaptor.getValue().authoritativeSource())
                .isEqualTo(PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT);
        assertThat(queryCaptor.getValue().sourceRevision()).isEqualTo(7L);
    }

    @Test
    void rejectsNonOverlappingYearBeforeCalendarQueries() {
        assertThatThrownBy(() -> service.getCalendar(
                PLAN_ID, PprEquipmentCalendarFilter.forYear(2028)))
                .isInstanceOf(RestException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(queryRepository, never()).findEquipmentPage(any());
    }

    @Test
    void hidesWorkerInvisiblePlanAndDoesNotQueryCalendarRows() {
        when(visibilityPolicy.isVisible(PlanStatus.DRAFT)).thenReturn(false);

        assertThatThrownBy(() -> service.getCalendar(
                PLAN_ID, PprEquipmentCalendarFilter.forYear(2026)))
                .isInstanceOf(RestException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);

        verify(queryRepository, never()).findEquipmentPage(any());
    }

    @Test
    void enforcesPlanDepartmentAndConstrainsNonAdminEquipmentToCurrentDepartment() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(PLAN_DEPARTMENT_ID);
        when(queryRepository.findOccurrences(any(), any())).thenReturn(List.of());

        service.getCalendar(PLAN_ID, new PprEquipmentCalendarFilter(
                2026, 0, 25, " pump ", PLAN_DEPARTMENT_ID, null, EQUIPMENT_ID,
                Set.of(MaintenanceKind.INSPECTION), Set.of(PprTaskStatus.PLANNED), true, false));

        verify(scopeAccessService).assertCanAccessDepartment(PLAN_DEPARTMENT_ID);
        ArgumentCaptor<PprEquipmentCalendarQueryRepository.CalendarQuery> queryCaptor =
                ArgumentCaptor.forClass(PprEquipmentCalendarQueryRepository.CalendarQuery.class);
        verify(queryRepository).findEquipmentPage(queryCaptor.capture());
        assertThat(queryCaptor.getValue().scopeDepartmentId()).isEqualTo(PLAN_DEPARTMENT_ID);
        assertThat(queryCaptor.getValue().planDepartmentId()).isEqualTo(PLAN_DEPARTMENT_ID);
        assertThat(queryCaptor.getValue().filter().search()).isEqualTo("pump");
        assertThat(queryCaptor.getValue().filter().equipmentId()).isEqualTo(EQUIPMENT_ID);
    }

    @Test
    void deniesNonAdminWithoutCurrentDepartmentAndRequiresAdminForNullPlanDepartment() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        assertThatThrownBy(() -> service.getCalendar(
                PLAN_ID, PprEquipmentCalendarFilter.forYear(2026)))
                .isInstanceOf(AccessDeniedException.class);

        PprPlan enterprisePlan = plan();
        enterprisePlan.setDepartmentId(null);
        when(planRepository.findByIdAndIsDeletedFalse(PLAN_ID)).thenReturn(Optional.of(enterprisePlan));
        assertThatThrownBy(() -> service.getCalendar(
                PLAN_ID, PprEquipmentCalendarFilter.forYear(2026)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private PprPlan plan() {
        PprPlan plan = new PprPlan();
        plan.setId(PLAN_ID);
        plan.setCode("PPR-2026");
        plan.setName("Year plan");
        plan.setStartDate(LocalDate.of(2025, 12, 15));
        plan.setEndDate(LocalDate.of(2027, 1, 15));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(PLAN_DEPARTMENT_ID);
        plan.setMaterializationMode(MaterializationMode.LEGACY_MATERIALIZED);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_APPLICABLE);
        return plan;
    }

    private CalendarEquipment equipment(UUID id, String name) {
        return equipment(id, name, false);
    }

    private CalendarEquipment equipment(UUID id, String name, boolean deleted) {
        return new CalendarEquipment(
                id, "EQ-1", name, "INV-1", "TECH-1", EquipmentStatus.ACTIVE, deleted,
                "A", PLAN_DEPARTMENT_ID, "DEP-P", "Physical",
                PLAN_DEPARTMENT_ID, "DEP-R", "Responsible",
                null, null, null,
                null, null, null,
                null, null, null);
    }

    private CalendarOccurrence occurrence(
            UUID taskId,
            UUID calculationItemId,
            LocalDate plannedDate,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDateTime dueDate,
            MaintenanceKind maintenanceKind) {
        return new CalendarOccurrence(
                EQUIPMENT_ID,
                PprEquipmentCalendarOccurrenceSourceType.MAINTENANCE_RULE,
                taskId,
                calculationItemId,
                null,
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                "RULE-1",
                "Rule",
                maintenanceKind,
                plannedDate,
                scheduledStart,
                scheduledEnd,
                dueDate,
                PprTaskStatus.PLANNED,
                PriorityLevel.MEDIUM,
                new BigDecimal("2.50"),
                "Work");
    }

    private CalendarOccurrence occurrenceFor(UUID equipmentId, UUID taskId, LocalDate plannedDate) {
        CalendarOccurrence base = occurrence(
                taskId,
                null,
                plannedDate,
                plannedDate.atTime(8, 0),
                plannedDate.atTime(10, 0),
                plannedDate.atTime(12, 0),
                MaintenanceKind.INSPECTION);
        return new CalendarOccurrence(
                equipmentId,
                base.sourceType(),
                base.taskId(),
                base.calculationItemId(),
                base.regulationId(),
                base.maintenanceRuleId(),
                base.sourceCode(),
                base.sourceName(),
                base.maintenanceKind(),
                base.plannedDate(),
                base.scheduledStart(),
                base.scheduledEnd(),
                base.dueDate(),
                base.status(),
                base.priority(),
                base.plannedLaborHours(),
                base.title());
    }
}
