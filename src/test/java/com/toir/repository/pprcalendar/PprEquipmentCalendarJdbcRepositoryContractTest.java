package com.toir.repository.pprcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSortDirection;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSortField;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarDiagnostics;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarOccurrence;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class PprEquipmentCalendarJdbcRepositoryContractTest {

    private static final UUID PLAN_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DEPARTMENT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Mock NamedParameterJdbcTemplate jdbc;

    private PprEquipmentCalendarJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PprEquipmentCalendarJdbcRepository(jdbc);
    }

    @Test
    void taskRowCountAndPageShareOccurrenceEquipmentAndPbacFilters() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of(EQUIPMENT_ID));

        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                2026,
                2,
                25,
                " Pump ",
                DEPARTMENT_ID,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                EQUIPMENT_ID,
                Set.of(MaintenanceKind.INSPECTION),
                Set.of(PprTaskStatus.PLANNED),
                true,
                false);
        repository.findEquipmentPage(taskQuery(filter));

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> countParameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(
                countSql.capture(), countParameters.capture(), eq(Long.class));
        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).query(
                pageSql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());

        assertThat(countSql.getValue())
                .contains("task.status <> 'CANCELLED'")
                .contains("task.status IN (:taskStatuses)")
                .contains("IN (:maintenanceKinds)")
                .contains("coalesce(e.responsible_department_id, e.department_id) = :planDepartmentId")
                .contains("coalesce(e.responsible_department_id, e.department_id) = :scopeDepartmentId")
                .contains("coalesce(e.responsible_department_id, e.department_id) = :departmentId")
                .contains("e.location_id = :locationId")
                .contains("e.id = :equipmentId")
                .contains("lower(coalesce(e.name, '')) LIKE :search");
        assertThat(pageSql.getValue())
                .contains(countSql.getValue().substring(0, countSql.getValue().indexOf("SELECT count(*)")))
                .contains("ORDER BY lower(coalesce(e.name, '')) ASC, e.id ASC")
                .contains("LIMIT :limit OFFSET :offset")
                .doesNotContain("e.is_deleted = false");
        MapSqlParameterSource parameters = (MapSqlParameterSource) countParameters.getValue();
        assertThat(parameters.getValue("search")).isEqualTo("%pump%");
        assertThat(parameters.getValue("taskStatuses")).isEqualTo(List.of("PLANNED"));
        assertThat(parameters.getValue("maintenanceKinds")).isEqualTo(List.of("INSPECTION"));
    }

    @Test
    void includeCancelledAndFalseOnlyWithWorkHaveExplicitSemantics() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of());

        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                2026, 0, 25, null, null, null, null,
                Set.of(), Set.of(), false, true);
        repository.findEquipmentPage(taskQuery(filter));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(
                sql.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertThat(sql.getValue())
                .doesNotContain("task.status <> 'CANCELLED'")
                .contains("UNION")
                .contains("FROM ppr_plan_targets target")
                .contains("target.target_type = 'EQUIPMENT'")
                .contains("target.target_type = 'EQUIPMENT_TYPE'")
                .contains("scoped_equipment.equipment_type_id =");
    }

    @Test
    void monthEquipmentTypeAndControlledSortAreBoundWithoutRawSqlInput() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
                .thenReturn(List.of());
        UUID equipmentTypeId =
                UUID.fromString("50000000-0000-0000-0000-000000000001");
        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                2026,
                7,
                0,
                25,
                null,
                null,
                null,
                null,
                equipmentTypeId,
                Set.of(),
                Set.of(),
                true,
                false,
                PprEquipmentCalendarSortField.INVENTORY_NUMBER,
                PprEquipmentCalendarSortDirection.DESC);

        repository.findEquipmentPage(taskQuery(filter));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(
                sql.capture(), params.capture(), eq(Long.class));
        assertThat(sql.getValue())
                .contains("BETWEEN :windowStart AND :windowEnd")
                .contains("e.equipment_type_id = :equipmentTypeId");
        org.mockito.Mockito.verify(jdbc).query(
                org.mockito.ArgumentMatchers.<String>argThat(value ->
                        value.contains(
                                "ORDER BY lower(coalesce(e.inventory_number, '')) DESC, e.id ASC")),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());
        MapSqlParameterSource parameters =
                (MapSqlParameterSource) params.getValue();
        assertThat(parameters.getValue("windowStart"))
                .isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(parameters.getValue("windowEnd"))
                .isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(parameters.getValue("equipmentTypeId"))
                .isEqualTo(equipmentTypeId);
    }

    @Test
    void taskAndSnapshotOccurrenceQueriesRemainMutuallyExclusive() {
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarOccurrence>>any()))
                .thenReturn(List.of());

        repository.findOccurrences(
                taskQuery(PprEquipmentCalendarFilter.forYear(2026)),
                List.of(EQUIPMENT_ID));
        ArgumentCaptor<String> taskSql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).query(
                taskSql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarOccurrence>>any());
        assertThat(taskSql.getValue())
                .contains("FROM ppr_tasks task")
                .contains("coalesce(linked.planned_date, cast(task.scheduled_start AS date))")
                .doesNotContain("item.calculation_revision = :sourceRevision");

        org.mockito.Mockito.clearInvocations(jdbc);
        PprEquipmentCalendarFilter snapshotFilter = new PprEquipmentCalendarFilter(
                2026, 0, 25, null, null, null, null,
                Set.of(MaintenanceKind.OVERHAUL),
                Set.of(PprTaskStatus.CANCELLED),
                true,
                false);
        repository.findOccurrences(snapshotQuery(snapshotFilter), List.of(EQUIPMENT_ID));
        ArgumentCaptor<String> snapshotSql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).query(
                snapshotSql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarOccurrence>>any());
        assertThat(snapshotSql.getValue())
                .contains("FROM maintenance_schedule_calculation_items item")
                .contains("item.calculation_revision = :sourceRevision")
                .contains("item.source_code_snapshot AS source_code")
                .contains("item.source_name_snapshot AS source_name")
                .contains("item.maintenance_type IN (:maintenanceKinds)")
                .contains("AND 1 = 0")
                .doesNotContain("FROM ppr_tasks task")
                .doesNotContain("JOIN maintenance_regulations")
                .doesNotContain("JOIN equipment_maintenance_rules")
                .doesNotContain("task.status");
    }

    @Test
    void exactMaterializedRevisionUsesSnapshotMetadataInsteadOfMutableReferences() {
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarOccurrence>>any()))
                .thenReturn(List.of());

        repository.findOccurrences(
                taskQuery(PprEquipmentCalendarFilter.forYear(2026), 7L),
                List.of(EQUIPMENT_ID));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).query(
                sql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarOccurrence>>any());
        assertThat(sql.getValue())
                .contains("linked.plan_id = :planId")
                .contains("linked.calculation_revision = :sourceRevision")
                .contains("linked.source_code_snapshot AS source_code")
                .contains("linked.source_name_snapshot AS source_name")
                .contains("linked.maintenance_type AS maintenance_kind")
                .doesNotContain(
                        "coalesce(maintenance_rule.code, regulation.code) AS source_code");
    }

    @Test
    void diagnosticsAreAggregateOnlyAndNeverPageLimited() {
        when(jdbc.queryForObject(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarDiagnostics>>any()))
                .thenReturn(new CalendarDiagnostics(1, 2, 3, 4));

        CalendarDiagnostics diagnostics = repository.findDiagnostics(
                taskQuery(PprEquipmentCalendarFilter.forYear(2026)));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(
                sql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CalendarDiagnostics>>any());
        assertThat(diagnostics).isEqualTo(new CalendarDiagnostics(1, 2, 3, 4));
        assertThat(sql.getValue())
                .contains("count(*) FILTER")
                .contains("AS missing_equipment")
                .contains("AS unresolved_equipment")
                .contains("AS outside_plan_year")
                .contains("AS outside_plan_range")
                .doesNotContain("LIMIT :limit")
                .doesNotContain("OFFSET :offset");
    }

    private CalendarQuery taskQuery(PprEquipmentCalendarFilter filter) {
        return taskQuery(filter, null);
    }

    private CalendarQuery taskQuery(
            PprEquipmentCalendarFilter filter, Long sourceRevision) {
        return new CalendarQuery(
                PLAN_ID,
                DEPARTMENT_ID,
                DEPARTMENT_ID,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                PprEquipmentCalendarAuthoritativeSource.PPR_TASK,
                sourceRevision,
                filter);
    }

    private CalendarQuery snapshotQuery(PprEquipmentCalendarFilter filter) {
        return new CalendarQuery(
                PLAN_ID,
                DEPARTMENT_ID,
                DEPARTMENT_ID,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT,
                7L,
                filter);
    }
}
